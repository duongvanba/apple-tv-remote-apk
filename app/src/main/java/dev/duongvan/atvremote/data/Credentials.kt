package dev.duongvan.atvremote.data

/** Long term HomeKit credentials obtained from a successful pair-setup. */
data class Credentials(
    val ltpk: ByteArray,
    val ltsk: ByteArray,
    val atvId: ByteArray,
    val clientId: ByteArray
) {
    fun serialize(): String = listOf(ltpk, ltsk, atvId, clientId).joinToString(":") { it.toHex() }

    override fun equals(other: Any?): Boolean =
        other is Credentials && serialize() == other.serialize()

    override fun hashCode(): Int = serialize().hashCode()

    companion object {
        fun parse(value: String): Credentials? {
            val parts = value.split(":")
            if (parts.size != 4) return null
            return runCatching {
                Credentials(parts[0].fromHex(), parts[1].fromHex(), parts[2].fromHex(), parts[3].fromHex())
            }.getOrNull()
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.fromHex(): ByteArray {
    require(length % 2 == 0) { "invalid hex string" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
