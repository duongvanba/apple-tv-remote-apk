package dev.duongvan.atvremote.data

import android.content.Context

/** Persists pairing credentials and the last used device. */
class DeviceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("atv-remote", Context.MODE_PRIVATE)

    fun credentialsFor(deviceId: String): Credentials? =
        prefs.getString("creds:$deviceId", null)?.let { Credentials.parse(it) }

    fun saveCredentials(deviceId: String, credentials: Credentials) {
        prefs.edit().putString("creds:$deviceId", credentials.serialize()).apply()
    }

    fun forget(deviceId: String) {
        prefs.edit().remove("creds:$deviceId").apply()
    }

    var lastDevice: AtvDeviceRecord?
        get() {
            val raw = prefs.getString("last-device", null) ?: return null
            val parts = raw.split("|")
            if (parts.size < 3) return null
            val port = parts[2].toIntOrNull() ?: return null
            return AtvDeviceRecord(parts[0], parts[1], port)
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("last-device").apply()
            } else {
                prefs.edit()
                    .putString("last-device", "${value.name}|${value.host}|${value.port}")
                    .apply()
            }
        }
}

data class AtvDeviceRecord(val name: String, val host: String, val port: Int) {
    val id: String get() = "$host:$port"
}
