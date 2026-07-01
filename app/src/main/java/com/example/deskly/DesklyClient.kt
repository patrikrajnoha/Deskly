package com.example.deskly

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object DesklyClient {
    enum class ConnectionType {
        LAN,
        BLUETOOTH
    }

    data class State(
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val authenticating: Boolean = false,
        val authorized: Boolean = false,
        val serverIp: String? = null,
        val serverPort: Int? = null,
        val connectionType: ConnectionType = ConnectionType.LAN,
        val lastError: String? = null
    )

    interface Listener {
        fun onState(state: State) {}
        fun onLog(line: String) {}
        fun onJson(json: JSONObject) {}
    }

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val REQUEST_TIMEOUT_MS = 10_000L
    private const val MAX_HB_FAILS = 3
    private const val PERF_LOG_WINDOW_MS = 30_000L

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val stateRef = AtomicReference(State())
    val state: State get() = stateRef.get()

    private val listeners = CopyOnWriteArraySet<Listener>()

    fun setListener(l: Listener?) {
        listeners.clear()
        if (l != null) listeners.add(l)
    }

    fun addListener(l: Listener) {
        listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    private var socket: Socket? = null
    private var bluetoothSocket: BluetoothSocket? = null
    @Volatile private var connectingSocket: Socket? = null
    @Volatile private var connectingBluetoothSocket: BluetoothSocket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    private val writeMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val connectionGeneration = AtomicInteger(0)

    @Volatile private var token: String? = null
    @Volatile private var performancePolicy = PerformancePolicy()
    @Volatile private var diagnosticsEnabled = false
    private val sentCommands = AtomicLong(0)
    private val failedSends = AtomicLong(0)
    private val lastPerfLogAt = AtomicLong(System.currentTimeMillis())

    fun configurePerformance(policy: PerformancePolicy, diagnostics: Boolean) {
        performancePolicy = policy
        diagnosticsEnabled = diagnostics
    }

    private fun log(s: String) {
        mainScope.launch { listeners.forEach { it.onLog(s) } }
    }

    private fun logJson(prefix: String, json: JSONObject) {
        val type = json.optString("type", "?").ifBlank { "?" }
        val rid = if (json.optString("rid", "").isBlank()) "no" else "yes"
        val ok = if (json.has("ok")) " ok=${json.optBoolean("ok", false)}" else ""
        log("$prefix type=$type rid=$rid$ok")
    }

    private fun updateState(block: (State) -> State) {
        val newState = block(stateRef.get())
        stateRef.set(newState)
        mainScope.launch { listeners.forEach { it.onState(newState) } }
    }

    fun setToken(t: String?) {
        token = t
        log("Token set: ${if (t.isNullOrBlank()) "none" else "yes"}")
    }

    fun getToken(): String? = token

    private fun phoneName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()

        return when {
            manufacturer.isBlank() && model.isBlank() -> "Android phone"
            manufacturer.isBlank() -> model
            model.isBlank() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    fun connect(ip: String, port: Int, onDone: (ok: Boolean, err: String?) -> Unit) {
        ioScope.launch {
            val generation = connectionGeneration.incrementAndGet()
            try {
                closeInternal("replace connect", notify = false)
                updateState {
                    it.copy(
                        connecting = true,
                        connected = false,
                        authenticating = false,
                        authorized = false,
                        serverIp = ip,
                        serverPort = port,
                        connectionType = ConnectionType.LAN,
                        lastError = null
                    )
                }

                log("Connecting to $ip:$port")

                val s = Socket()
                connectingSocket = s
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                if (connectingSocket === s) connectingSocket = null

                if (connectionGeneration.get() != generation) {
                    try { s.close() } catch (_: Exception) {}
                    withContext(Dispatchers.Main) { onDone(false, "Connect replaced") }
                    return@launch
                }

                val nextReader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val nextWriter = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))

                if (connectionGeneration.get() != generation) {
                    try { nextReader.close() } catch (_: Exception) {}
                    try { nextWriter.close() } catch (_: Exception) {}
                    try { s.close() } catch (_: Exception) {}
                    withContext(Dispatchers.Main) { onDone(false, "Connect replaced") }
                    return@launch
                }

                socket = s
                reader = nextReader
                writer = nextWriter

                updateState {
                    it.copy(
                        connecting = false,
                        connected = true,
                        authenticating = false,
                        authorized = false,
                        lastError = null
                    )
                }

                startReaderLoop(generation)
                startHeartbeat(generation)

                withContext(Dispatchers.Main) { onDone(true, null) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                log("Connect failed: $msg")
                if (connectionGeneration.get() == generation) {
                    closeInternal("connect fail", notify = false)
                    updateState {
                        it.copy(
                            connecting = false,
                            connected = false,
                            authenticating = false,
                            authorized = false,
                            serverIp = ip,
                            serverPort = port,
                            lastError = msg
                        )
                    }
                }
                withContext(Dispatchers.Main) { onDone(false, msg) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(device: BluetoothDevice, onDone: (ok: Boolean, err: String?) -> Unit) {
        ioScope.launch {
            val generation = connectionGeneration.incrementAndGet()
            val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth PC"
            val address = runCatching { device.address }.getOrNull().orEmpty()
            try {
                closeInternal("replace bluetooth connect", notify = false)
                updateState {
                    it.copy(
                        connecting = true,
                        connected = false,
                        authenticating = false,
                        authorized = false,
                        serverIp = address,
                        serverPort = null,
                        connectionType = ConnectionType.BLUETOOTH,
                        lastError = null
                    )
                }

                log("Connecting to Bluetooth device: $name")

                val s = device.createRfcommSocketToServiceRecord(BluetoothProtocol.SERVICE_UUID)
                connectingBluetoothSocket = s
                s.connect()
                if (connectingBluetoothSocket === s) connectingBluetoothSocket = null

                if (connectionGeneration.get() != generation) {
                    try { s.close() } catch (_: Exception) {}
                    withContext(Dispatchers.Main) { onDone(false, "Connect replaced") }
                    return@launch
                }

                val nextReader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
                val nextWriter = BufferedWriter(OutputStreamWriter(s.outputStream, Charsets.UTF_8))

                if (connectionGeneration.get() != generation) {
                    try { nextReader.close() } catch (_: Exception) {}
                    try { nextWriter.close() } catch (_: Exception) {}
                    try { s.close() } catch (_: Exception) {}
                    withContext(Dispatchers.Main) { onDone(false, "Connect replaced") }
                    return@launch
                }

                bluetoothSocket = s
                reader = nextReader
                writer = nextWriter

                updateState {
                    it.copy(
                        connecting = false,
                        connected = true,
                        authenticating = false,
                        authorized = false,
                        serverIp = address,
                        serverPort = null,
                        connectionType = ConnectionType.BLUETOOTH,
                        lastError = null
                    )
                }

                startReaderLoop(generation)
                startHeartbeat(generation)

                withContext(Dispatchers.Main) { onDone(true, null) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                log("Bluetooth connect failed: $msg")
                if (connectionGeneration.get() == generation) {
                    closeInternal("bluetooth connect fail", notify = false)
                    updateState {
                        it.copy(
                            connecting = false,
                            connected = false,
                            authenticating = false,
                            authorized = false,
                            serverIp = address,
                            serverPort = null,
                            connectionType = ConnectionType.BLUETOOTH,
                            lastError = msg
                        )
                    }
                }
                withContext(Dispatchers.Main) { onDone(false, msg) }
            }
        }
    }

    fun close(reason: String = "manual") {
        connectionGeneration.incrementAndGet()
        ioScope.launch { closeInternal(reason) }
    }

    private fun closeInternal(reason: String, notify: Boolean = true) {
        log("Closing: $reason")

        heartbeatJob?.cancel()
        heartbeatJob = null

        readerJob?.cancel()
        readerJob = null

        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { bluetoothSocket?.close() } catch (_: Exception) {}
        try { connectingSocket?.close() } catch (_: Exception) {}
        try { connectingBluetoothSocket?.close() } catch (_: Exception) {}

        reader = null
        writer = null
        socket = null
        bluetoothSocket = null
        connectingSocket = null
        connectingBluetoothSocket = null

        pending.forEach { (_, d) ->
            d.completeExceptionally(RuntimeException("closed"))
        }
        pending.clear()

        if (notify) {
            updateState {
                it.copy(
                    connecting = false,
                    connected = false,
                    authenticating = false,
                    authorized = false
                )
            }
        }
    }

    fun pair(pin: String, onDone: (ok: Boolean, token: String?, msg: String) -> Unit) {
        ioScope.launch {
            try {
                val resp = request(
                    type = "pair_request",
                    payload = JSONObject()
                        .put("pin", pin)
                        .put("deviceName", phoneName()),
                    timeoutMs = REQUEST_TIMEOUT_MS
                )

                val ok = resp.optBoolean("ok", false)
                val msg = resp.optString("message", if (ok) "Paired" else "Pair failed")
                val t = resp.optJSONObject("data")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }

                if (ok && !t.isNullOrBlank()) setToken(t)

                withContext(Dispatchers.Main) { onDone(ok, t, msg) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                log("Pair failed: $msg")
                withContext(Dispatchers.Main) { onDone(false, null, msg) }
            }
        }
    }

    fun auth(token: String, onDone: (ok: Boolean, msg: String) -> Unit) {
        ioScope.launch {
            try {
                updateState { it.copy(authenticating = true, authorized = false, lastError = null) }

                val resp = request(
                    type = "auth_request",
                    payload = JSONObject()
                        .put("token", token)
                        .put("deviceName", phoneName()),
                    timeoutMs = 6000L
                )

                val ok = resp.optBoolean("ok", false)
                val msg = resp.optString("message", if (ok) "Authorized" else "Unauthorized")

                updateState {
                    it.copy(
                        authenticating = false,
                        authorized = ok,
                        lastError = if (ok) null else msg
                    )
                }

                withContext(Dispatchers.Main) { onDone(ok, msg) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                updateState { it.copy(authenticating = false, authorized = false, lastError = msg) }
                withContext(Dispatchers.Main) { onDone(false, msg) }
            }
        }
    }

    fun sendSecure(type: String, token: String, payload: JSONObject? = null) {
        val p = (payload ?: JSONObject()).put("token", token)
        ioScope.launch {
            try {
                sendRaw(withRid(type, p))
                sentCommands.incrementAndGet()
                maybeLogPerformance("send:$type")
            } catch (e: Exception) {
                failedSends.incrementAndGet()
                log("sendSecure($type) failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun pingSecure(onDone: (ok: Boolean, msg: String) -> Unit) {
        val t = token
        if (t.isNullOrBlank()) {
            onDone(false, "No token")
            return
        }

        ioScope.launch {
            try {
                val resp = request("ping_secure", JSONObject().put("token", t), 4000L)
                val ok = resp.optBoolean("ok", false)
                val msg = resp.optString("message", if (ok) "pong" else "ping failed")
                withContext(Dispatchers.Main) { onDone(ok, msg) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                withContext(Dispatchers.Main) { onDone(false, msg) }
            }
        }
    }

    private fun startReaderLoop(generation: Int) {
        readerJob?.cancel()
        readerJob = ioScope.launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    if (line.isBlank()) continue

                    val json = JSONObject(line)
                    logJson("<-", json)

                    val rid = json.optString("rid", "")
                    if (rid.isNotBlank()) {
                        pending.remove(rid)?.complete(json)
                    }

                    mainScope.launch { listeners.forEach { it.onJson(json) } }
                }
            } catch (e: Exception) {
                log("Reader loop ended: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                if (connectionGeneration.get() == generation) {
                    closeInternal("reader ended")
                } else {
                    log("Reader ended for replaced connection")
                }
            }
        }
    }

    private fun startHeartbeat(generation: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = ioScope.launch {
            var fails = 0
            while (isActive) {
                delay(performancePolicy.heartbeatMs)

                if (connectionGeneration.get() != generation) break
                if (!state.connected) continue
                if (!state.authorized) continue
                val t = token ?: continue

                try {
                    val started = System.currentTimeMillis()
                    val resp = request("ping_secure", JSONObject().put("token", t), 4000L)
                    if (diagnosticsEnabled) {
                        log("perf heartbeatLatencyMs=${System.currentTimeMillis() - started} intervalMs=${performancePolicy.heartbeatMs}")
                    }
                    val ok = resp.optBoolean("ok", false)
                    if (ok) {
                        fails = 0
                    } else {
                        fails++
                        val message = resp.optString("message")
                        log("Heartbeat not ok ($message)")
                        if (message.contains("Unauthorized", ignoreCase = true)) {
                            updateState {
                                it.copy(
                                    authenticating = false,
                                    authorized = false,
                                    lastError = message.ifBlank { "Unauthorized" }
                                )
                            }
                            if (connectionGeneration.get() == generation) {
                                closeInternal("unauthorized heartbeat")
                            }
                            break
                        }
                    }
                } catch (_: Exception) {
                    fails++
                }

                if (fails >= MAX_HB_FAILS) {
                    log("Heartbeat failed $fails times; disconnecting")
                    if (connectionGeneration.get() == generation) {
                        closeInternal("heartbeat fails")
                    }
                    fails = 0
                }
            }
        }
    }

    private fun maybeLogPerformance(reason: String) {
        if (!diagnosticsEnabled) return
        val now = System.currentTimeMillis()
        val last = lastPerfLogAt.get()
        if (now - last < PERF_LOG_WINDOW_MS) return
        if (!lastPerfLogAt.compareAndSet(last, now)) return

        val sent = sentCommands.getAndSet(0)
        val failed = failedSends.getAndSet(0)
        log(
            "perf reason=$reason sent=$sent failed=$failed pending=${pending.size} " +
                "heartbeatMs=${performancePolicy.heartbeatMs} lowPower=${performancePolicy.effectiveLowPower}"
        )
    }

    private suspend fun request(type: String, payload: JSONObject? = null, timeoutMs: Long): JSONObject {
        val rid = UUID.randomUUID().toString()
        val msg = DesklyProtocol.request(type, payload)
            .put("rid", rid)

        val def = CompletableDeferred<JSONObject>()
        pending[rid] = def

        return try {
            sendRaw(msg)
            withTimeout(timeoutMs) { def.await() }
        } finally {
            pending.remove(rid)
        }
    }

    private suspend fun sendRaw(json: JSONObject) {
        writeMutex.withLock {
            val w = writer ?: throw IllegalStateException("Not connected")
            val line = json.toString()
            logJson("->", json)
            w.write(line)
            w.newLine()
            w.flush()
        }
    }

    private fun withRid(type: String, payload: JSONObject): JSONObject {
        return DesklyProtocol.request(type, payload)
            .put("rid", UUID.randomUUID().toString())
    }
}
