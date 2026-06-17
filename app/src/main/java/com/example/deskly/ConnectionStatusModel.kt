package com.example.deskly

object ConnectionStatusModel {
    enum class ConnectionType {
        LAN,
        BLUETOOTH
    }

    data class ViewState(
        val status: String,
        val auth: String,
        val pcName: String,
        val address: String,
        val connectionType: ConnectionType
    )

    fun build(
        state: DesklyClient.State,
        savedPcName: String?,
        savedIp: String?,
        savedPort: Int?,
        hasToken: Boolean,
        authRejected: Boolean
    ): ViewState {
        val status = when {
            state.authorized -> "Connected"
            state.authenticating || state.connecting -> "Reconnecting"
            authRejected -> "Auth Failed"
            state.connected -> "Pair Required"
            state.lastError?.isNotBlank() == true -> "Offline"
            else -> "Offline"
        }

        val auth = when {
            state.authorized -> "Authorized"
            state.authenticating -> "Authorizing"
            authRejected -> "Auth failed"
            state.connected && !hasToken -> "Pair Required"
            hasToken -> "Paired"
            else -> "Pair Required"
        }

        val pcName = savedPcName?.trim()?.takeIf { it.isNotBlank() }
            ?: if (!savedIp.isNullOrBlank()) "Saved PC" else "No PC selected"
        val address = if (!savedIp.isNullOrBlank() && savedPort != null) {
            "$savedIp:$savedPort"
        } else {
            "Choose a PC first"
        }

        return ViewState(
            status = status,
            auth = auth,
            pcName = pcName,
            address = address,
            connectionType = ConnectionType.LAN
        )
    }
}
