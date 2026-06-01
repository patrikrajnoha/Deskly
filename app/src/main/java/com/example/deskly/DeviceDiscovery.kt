package com.example.deskly

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object DeviceDiscovery {

    private const val DISCOVER_MAGIC = "DESKLY_DISCOVER"
    private const val UDP_PORT = 5051

    /**
     * Pošle broadcast a ~timeoutMs zbiera odpovede.
     */
    fun scan(timeoutMs: Int = 1200): List<DiscoveredDevice> {
        val found = LinkedHashMap<String, DiscoveredDevice>()

        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.soTimeout = 300 // krátky timeout, aby sme vedeli opakovane čítať

            val data = DISCOVER_MAGIC.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName("255.255.255.255"),
                UDP_PORT
            )
            sock.send(packet)

            val start = System.currentTimeMillis()
            val buf = ByteArray(4096)

            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    val recv = DatagramPacket(buf, buf.size)
                    sock.receive(recv)

                    val text = String(recv.data, 0, recv.length, Charsets.UTF_8).trim()
                    val json = JSONObject(text)

                    if (json.optString("type") != "discover_response") continue

                    val id = json.optString("id").trim()
                    val name = json.optString("name").trim().ifEmpty { "Deskly PC" }

                    val ip = json.optString("ip").trim().ifEmpty {
                        recv.address?.hostAddress ?: ""
                    }

                    val portRaw = json.optInt("port", 5050)
                    val port = if (portRaw in 1..65535) portRaw else 5050

                    if (id.isNotEmpty() && ip.isNotEmpty()) {
                        found[id] = DiscoveredDevice(id, name, ip, port)
                    }
                } catch (_: Exception) {
                    // ignore (timeout / parse)
                }
            }
        }

        return found.values.toList()
    }
}
