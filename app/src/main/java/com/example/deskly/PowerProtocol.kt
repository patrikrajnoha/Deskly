package com.example.deskly

import org.json.JSONObject

object PowerProtocol {
    const val TYPE_LOCK = "power_lock"
    const val TYPE_SLEEP = "power_sleep"
    const val TYPE_SHUTDOWN = "power_shutdown"
    const val TYPE_RESTART = "power_restart"

    val dangerousActions = setOf(TYPE_SLEEP, TYPE_SHUTDOWN, TYPE_RESTART)

    fun requiresConfirmation(type: String): Boolean =
        type in dangerousActions

    fun payload(type: String, issuedAtUtcMs: Long = System.currentTimeMillis(), fadeOutVolume: Boolean = false): JSONObject {
        val payload = JSONObject().put("issuedAtUtcMs", issuedAtUtcMs)
        if (requiresConfirmation(type)) {
            payload.put("confirmed", true)
        }
        if (type == TYPE_SHUTDOWN) {
            payload.put("fadeOutVolume", fadeOutVolume)
        }
        return payload
    }
}
