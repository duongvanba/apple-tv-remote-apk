package dev.duongvan.atvremote.proto

import java.io.ByteArrayOutputStream

/** TLV8 encoding used by the HomeKit pairing procedure. */
object Tlv8 {

    const val METHOD = 0x00
    const val IDENTIFIER = 0x01
    const val SALT = 0x02
    const val PUBLIC_KEY = 0x03
    const val PROOF = 0x04
    const val ENCRYPTED_DATA = 0x05
    const val SEQ_NO = 0x06
    const val ERROR = 0x07
    const val BACK_OFF = 0x08
    const val SIGNATURE = 0x0A
    const val PERMISSIONS = 0x0B
    const val NAME = 0x11

    fun read(data: ByteArray): Map<Int, ByteArray> {
        val result = LinkedHashMap<Int, ByteArray>()
        var pos = 0
        while (pos + 1 < data.size) {
            val tag = data[pos].toInt() and 0xFF
            val length = data[pos + 1].toInt() and 0xFF
            val end = minOf(pos + 2 + length, data.size)
            val value = data.copyOfRange(pos + 2, end)
            result[tag] = result[tag]?.plus(value) ?: value
            pos = pos + 2 + length
        }
        return result
    }

    fun write(values: Map<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((tag, value) in values) {
            var pos = 0
            do {
                val size = minOf(value.size - pos, 255)
                out.write(tag)
                out.write(size)
                out.write(value, pos, size)
                pos += size
            } while (pos < value.size)
        }
        return out.toByteArray()
    }

    /** Human readable summary of an error TLV, used for pairing failures. */
    fun errorMessage(tlv: Map<Int, ByteArray>): String? {
        val error = tlv[ERROR] ?: return null
        val code = error.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        val name = when (code) {
            0x02 -> "sai mã PIN"
            0x03 -> "thiết bị yêu cầu chờ (back off)"
            0x04 -> "đã đạt số thiết bị ghép nối tối đa"
            0x05 -> "nhập sai quá nhiều lần"
            0x06 -> "không khả dụng"
            0x07 -> "thiết bị đang bận"
            else -> "lỗi không xác định ($code)"
        }
        return name
    }
}
