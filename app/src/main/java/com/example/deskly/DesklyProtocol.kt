package com.example.deskly

import org.json.JSONObject

object DesklyProtocol {
    const val VERSION = 1
    private val supportedVersions = setOf(VERSION)

    fun isSupportedVersion(version: Int?): Boolean =
        version == null || version in supportedVersions

    fun isKnownRequestType(type: String?): Boolean =
        DesklyCommands.isKnown(type)

    fun responseTypeFor(type: String?): String =
        DesklyCommands.responseTypeFor(type)

    fun requiresToken(type: String?): Boolean =
        DesklyCommands.requiresToken(type)

    fun isPrivileged(type: String?): Boolean =
        DesklyCommands.isPrivileged(type)

    fun request(type: String, payload: JSONObject? = null): JSONObject {
        return JSONObject()
            .put("type", type)
            .put("protocolVersion", VERSION)
            .put("payload", payload ?: JSONObject())
    }
}
