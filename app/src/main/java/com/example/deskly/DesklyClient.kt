package com.example.deskly

import kotlinx.coroutines.*
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
import java.util.concurrent.atomic.AtomicReference

object DesklyClient {

    data class State(
        val connected: Boolean = false,
        val authorized: Boolean = false,
        val serverIp: String? = null,
        val serverPort: Int? = null,
        val lastError: String? = null
    )

    interface Listener {
        fun onState(state: State) {}
        fun onLog(line: String) {}
        fun onJson(json: JSONObject) {}
    }

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val REQUEST_TIMEOUT_MS = 10_000L
    private const val HEARTBEAT_MS = 10_000L
    private const val MAX_HB_FAILS = 3

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val stateRef = AtomicReference(State())
    val state: State get() = stateRef.get()

    // ✅ Multi-listener (aby Settings neprebil Main)
    private val listeners = CopyOnWriteArraySet<Listener>()

    /** Backward compatible: správa sa ako "set jediného", ale my to premapujeme */
    fun setListener(l: Listener?) {
        listeners.clear()
        if (l != null) listeners.add(l)
    }

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    private val writeMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    @Volatile private var token: String? = null

    private fun log(s: String) {
        mainScope.launch { listeners.forEach { it.onLog(s) } }
    }

    private fun updateState(block: (State) -> State) {
        val newState = block(stateRef.get())
        stateRef.set(newState)
        mainScope.launch { listeners.forEach { it.onState(newState) } }
    }

    fun setToken(t: String?) {
        token = t
        log("🔑 Token set: ${if (t.isNullOrBlank()) "null" else "yes"}")
    }

    fun getToken(): String? = token

    fun connect(ip: String, port: Int, onDone: (ok: Boolean, err: String?) -> Unit) {
        ioScope.launch {
            try {
                closeInternal("replace connect")
                updateState { it.copy(connected = false, authorized = false, serverIp = ip, serverPort = port, lastError = null) }

                log("🔌 Connecting to $ip:$port …")

                val s = Socket()
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)

                socket = s
                reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))

                updateState { st -> st.copy(connected = true, authorized = false, lastError = null) }

                startReaderLoop()
                startHeartbeat()

                withContext(Dispatchers.Main) { onDone(true, null) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                log("❌ Connect failed: $msg")
                updateState { it.copy(connected = false, authorized = false, lastError = msg) }
                closeInternal("connect fail")
                withContext(Dispatchers.Main) { onDone(false, msg) }
            }
        }
    }

    fun close(reason: String = "manual") {
        ioScope.launch { closeInternal(reason) }
    }

    private fun closeInternal(reason: String) {
        log("🔌 Closing: $reason")

        heartbeatJob?.cancel()
        heartbeatJob = null

        readerJob?.cancel()
        readerJob = null

        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}

        reader = null
        writer = null
        socket = null

        pending.forEach { (_, d) ->
            d.completeExceptionally(RuntimeException("closed"))
        }
        pending.clear()

        updateState { it.copy(connected = false, authorized = false) }
    }

    fun pair(pin: String, onDone: (ok: Boolean, token: String?, msg: String) -> Unit) {
        ioScope.launch {
            try {
                val resp = request(
                    type = "pair_request",
                    payload = JSONObject().put("pin", pin),
                    timeoutMs = REQUEST_TIMEOUT_MS
                )

                val ok = resp.optBoolean("ok", false)
                val msg = resp.optString("message", if (ok) "Paired" else "Pair failed")

                // ✅ FIX: optString default nemôže byť null
                val t = resp.optJSONObject("data")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }

                if (ok && !t.isNullOrBlank()) setToken(t)

                withContext(Dispatchers.Main) { onDone(ok, t, msg) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                log("❌ Pair failed: $msg")
                withContext(Dispatchers.Main) { onDone(false, null, msg) }
            }
        }
    }

    fun auth(token: String, onDone: (ok: Boolean, msg: String) -> Unit) {
        ioScope.launch {
            try {
                val resp = request(
                    type = "auth_request",
                    payload = JSONObject().put("token", token),
                    timeoutMs = 6000L
                )

                val ok = resp.optBoolean("ok", false)
                val msg = resp.optString("message", if (ok) "Authorized" else "Unauthorized")

                updateState { st -> st.copy(authorized = ok) }

                withContext(Dispatchers.Main) { onDone(ok, msg) }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                updateState { st -> st.copy(authorized = false, lastError = msg) }
                withContext(Dispatchers.Main) { onDone(false, msg) }
            }
        }
    }

    fun sendSecure(type: String, token: String, payload: JSONObject? = null) {
        val p = (payload ?: JSONObject()).put("token", token)
        ioScope.launch {
            try {
                sendRaw(withRid(type, p))
            } catch (e: Exception) {
                log("❌ sendSecure($type) failed: ${e.message ?: e.javaClass.simpleName}")
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

    // ===== Internals =====

    private fun startReaderLoop() {
        readerJob?.cancel()
        readerJob = ioScope.launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    if (line.isBlank()) continue

                    log("⬅ $line")
                    val json = JSONObject(line)

                    val rid = json.optString("rid", "")
                    if (rid.isNotBlank()) {
                        pending.remove(rid)?.complete(json)
                    }

                    mainScope.launch { listeners.forEach { it.onJson(json) } }
                }
            } catch (e: Exception) {
                log("❌ Reader loop crashed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                closeInternal("reader ended")
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = ioScope.launch {
            var fails = 0
            while (isActive) {
                delay(HEARTBEAT_MS)

                if (!state.connected) continue
                if (!state.authorized) continue
                val t = token ?: continue

                try {
                    val resp = request("ping_secure", JSONObject().put("token", t), 4000L)
                    val ok = resp.optBoolean("ok", false)
                    if (ok) {
                        fails = 0
                    } else {
                        fails++
                        log("⚠ Heartbeat not ok (${resp.optString("message")})")
                    }
                } catch (_: Exception) {
                    fails++
                }

                if (fails >= MAX_HB_FAILS) {
                    log("⚠ Heartbeat failed $fails× → disconnect")
                    closeInternal("heartbeat fails")
                    fails = 0
                }
            }
        }
    }

    private suspend fun request(type: String, payload: JSONObject? = null, timeoutMs: Long): JSONObject {
        val rid = UUID.randomUUID().toString()
        val msg = JSONObject()
            .put("rid", rid)
            .put("type", type)
            .put("payload", payload ?: JSONObject())

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
            log("➡ $line")
            w.write(line)
            w.newLine()
            w.flush()
        }
    }

    private fun withRid(type: String, payload: JSONObject): JSONObject {
        return JSONObject()
            .put("rid", UUID.randomUUID().toString())
            .put("type", type)
            .put("payload", payload)
    }
}
