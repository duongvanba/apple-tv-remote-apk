package dev.duongvan.atvremote.proto

import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap

/**
 * Minimal binary property list (bplist00) reader/writer.
 *
 * Only the subset needed by the RTI keyboard payloads is covered, but the
 * writer reproduces CPython's `plistlib` byte for byte so the generated
 * NSKeyedArchiver blobs match what pyatv sends.
 */
object BinaryPlist {

    /** Reference into the `$objects` table of an NSKeyedArchiver payload. */
    class Uid(val value: Int) {
        override fun toString(): String = "Uid($value)"
    }

    private val HEADER = "bplist00".toByteArray(Charsets.US_ASCII)

    // ----------------------------------------------------------------- write

    fun encode(root: Any?): ByteArray {
        val objects = ArrayList<Any?>()
        val scalars = HashMap<String, Int>()
        val identities = IdentityHashMap<Any, Int>()
        var nullRef = -1

        fun scalarKey(value: Any): String? = when (value) {
            is String -> "s:$value"
            is Int -> "i:${value.toLong()}"
            is Long -> "i:$value"
            is Double -> "d:$value"
            is ByteArray -> "b:" + value.joinToString("") { "%02x".format(it) }
            else -> null
        }

        fun flatten(value: Any?) {
            if (value == null) {
                if (nullRef >= 0) return
                nullRef = objects.size
                objects.add(null)
                return
            }
            val key = scalarKey(value)
            if (key != null) {
                if (scalars.containsKey(key)) return
                scalars[key] = objects.size
            } else {
                if (identities.containsKey(value)) return
                identities[value] = objects.size
            }
            objects.add(value)

            when (value) {
                is Map<*, *> -> {
                    // plistlib emits every key first, then every value.
                    value.keys.forEach { flatten(it) }
                    value.values.forEach { flatten(it) }
                }
                is List<*> -> value.forEach { flatten(it) }
                else -> Unit
            }
        }

        fun refOf(value: Any?): Int = when {
            value == null -> nullRef
            scalarKey(value) != null -> scalars.getValue(scalarKey(value)!!)
            else -> identities.getValue(value)
        }

        flatten(root)

        val refSize = countToSize(objects.size)
        val body = ByteArrayOutputStream()
        body.write(HEADER)
        val offsets = IntArray(objects.size)
        for (index in objects.indices) {
            offsets[index] = body.size()
            writeObject(objects[index], body, refSize, ::refOf)
        }

        val offsetTableOffset = body.size()
        val offsetSize = countToSize(offsetTableOffset)
        for (offset in offsets) writeBigEndian(body, offset.toLong(), offsetSize)

        body.write(ByteArray(5))
        body.write(0) // sort version
        body.write(offsetSize)
        body.write(refSize)
        writeBigEndian(body, objects.size.toLong(), 8)
        writeBigEndian(body, 0L, 8)
        writeBigEndian(body, offsetTableOffset.toLong(), 8)
        return body.toByteArray()
    }

    private fun countToSize(count: Int): Int = when {
        count < 1 shl 8 -> 1
        count < 1 shl 16 -> 2
        else -> 4
    }

    private fun writeBigEndian(out: ByteArrayOutputStream, value: Long, size: Int) {
        for (i in size - 1 downTo 0) out.write(((value shr (8 * i)) and 0xFF).toInt())
    }

    private fun writeSize(out: ByteArrayOutputStream, token: Int, size: Int) {
        if (size < 15) {
            out.write(token or size)
        } else {
            out.write(token or 0xF)
            when {
                size < 1 shl 8 -> { out.write(0x10); writeBigEndian(out, size.toLong(), 1) }
                size < 1 shl 16 -> { out.write(0x11); writeBigEndian(out, size.toLong(), 2) }
                else -> { out.write(0x12); writeBigEndian(out, size.toLong(), 4) }
            }
        }
    }

    private fun writeObject(
        value: Any?,
        out: ByteArrayOutputStream,
        refSize: Int,
        refOf: (Any?) -> Int
    ) {
        when (value) {
            null -> out.write(0x00)
            is Boolean -> out.write(if (value) 0x09 else 0x08)
            is Uid -> when {
                value.value < 1 shl 8 -> { out.write(0x80); writeBigEndian(out, value.value.toLong(), 1) }
                value.value < 1 shl 16 -> { out.write(0x81); writeBigEndian(out, value.value.toLong(), 2) }
                else -> { out.write(0x83); writeBigEndian(out, value.value.toLong(), 4) }
            }
            is Int, is Long -> {
                val number = (value as Number).toLong()
                when {
                    number < 1L shl 8 -> { out.write(0x10); writeBigEndian(out, number, 1) }
                    number < 1L shl 16 -> { out.write(0x11); writeBigEndian(out, number, 2) }
                    number < 1L shl 32 -> { out.write(0x12); writeBigEndian(out, number, 4) }
                    else -> { out.write(0x13); writeBigEndian(out, number, 8) }
                }
            }
            is Double -> {
                out.write(0x23)
                writeBigEndian(out, value.toRawBits(), 8)
            }
            is ByteArray -> {
                writeSize(out, 0x40, value.size)
                out.write(value)
            }
            is String -> {
                val ascii = value.all { it.code < 128 }
                if (ascii) {
                    writeSize(out, 0x50, value.length)
                    out.write(value.toByteArray(Charsets.US_ASCII))
                } else {
                    val encoded = value.toByteArray(Charsets.UTF_16BE)
                    writeSize(out, 0x60, encoded.size / 2)
                    out.write(encoded)
                }
            }
            is List<*> -> {
                writeSize(out, 0xA0, value.size)
                value.forEach { writeBigEndian(out, refOf(it).toLong(), refSize) }
            }
            is Map<*, *> -> {
                writeSize(out, 0xD0, value.size)
                value.keys.forEach { writeBigEndian(out, refOf(it).toLong(), refSize) }
                value.values.forEach { writeBigEndian(out, refOf(it).toLong(), refSize) }
            }
            else -> throw IllegalArgumentException("cannot encode ${value.javaClass}")
        }
    }

    // ------------------------------------------------------------------ read

    fun decode(data: ByteArray): Any? {
        require(data.size > 40 && data.copyOfRange(0, 8).contentEquals(HEADER)) {
            "not a binary plist"
        }
        val trailer = data.size - 32
        val offsetSize = data[trailer + 6].toInt() and 0xFF
        val refSize = data[trailer + 7].toInt() and 0xFF
        val count = readBigEndian(data, trailer + 8, 8).toInt()
        val topRef = readBigEndian(data, trailer + 16, 8).toInt()
        val offsetTableOffset = readBigEndian(data, trailer + 24, 8).toInt()

        val offsets = IntArray(count) {
            readBigEndian(data, offsetTableOffset + it * offsetSize, offsetSize).toInt()
        }
        return readObject(data, offsets, refSize, topRef)
    }

    private fun readBigEndian(data: ByteArray, position: Int, size: Int): Long {
        var value = 0L
        for (i in 0 until size) value = (value shl 8) or (data[position + i].toLong() and 0xFF)
        return value
    }

    private fun readObject(data: ByteArray, offsets: IntArray, refSize: Int, index: Int): Any? {
        var pos = offsets[index]
        val marker = data[pos].toInt() and 0xFF
        pos++

        fun readLength(low: Int): Int {
            if (low != 0xF) return low
            val sizeMarker = data[pos].toInt() and 0xFF
            pos++
            val bytes = 1 shl (sizeMarker and 0xF)
            val length = readBigEndian(data, pos, bytes).toInt()
            pos += bytes
            return length
        }

        return when (marker and 0xF0) {
            0x00 -> when (marker) {
                0x00 -> null
                0x08 -> false
                0x09 -> true
                else -> null
            }
            0x10 -> readBigEndian(data, pos, 1 shl (marker and 0xF))
            0x20 -> if ((marker and 0xF) == 2) {
                Float.fromBits(readBigEndian(data, pos, 4).toInt()).toDouble()
            } else {
                Double.fromBits(readBigEndian(data, pos, 8))
            }
            0x40 -> {
                val length = readLength(marker and 0xF)
                data.copyOfRange(pos, pos + length)
            }
            0x50 -> {
                val length = readLength(marker and 0xF)
                String(data, pos, length, Charsets.US_ASCII)
            }
            0x60 -> {
                val length = readLength(marker and 0xF)
                String(data, pos, length * 2, Charsets.UTF_16BE)
            }
            0x80 -> Uid(readBigEndian(data, pos, (marker and 0xF) + 1).toInt())
            0xA0 -> {
                val length = readLength(marker and 0xF)
                List(length) {
                    readObject(
                        data,
                        offsets,
                        refSize,
                        readBigEndian(data, pos + it * refSize, refSize).toInt()
                    )
                }
            }
            0xD0 -> {
                val length = readLength(marker and 0xF)
                val result = LinkedHashMap<Any?, Any?>()
                for (i in 0 until length) {
                    val keyRef = readBigEndian(data, pos + i * refSize, refSize).toInt()
                    val valueRef =
                        readBigEndian(data, pos + (length + i) * refSize, refSize).toInt()
                    result[readObject(data, offsets, refSize, keyRef)] =
                        readObject(data, offsets, refSize, valueRef)
                }
                result
            }
            else -> throw IllegalArgumentException("unsupported plist marker 0x%02X".format(marker))
        }
    }

    /**
     * Follows a `$top` path through the `$objects` table of an NSKeyedArchiver
     * payload, resolving UID references along the way.
     */
    fun archiveProperty(archive: ByteArray, vararg path: String): Any? {
        val root = decode(archive) as? Map<*, *> ?: return null
        val objects = root["\$objects"] as? List<*> ?: return null
        var element: Any? = root["\$top"]
        for (key in path) {
            element = (element as? Map<*, *>)?.get(key) ?: return null
            if (element is Uid) element = objects.getOrNull(element.value)
        }
        return element
    }
}
