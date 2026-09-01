package dev.duongvan.atvremote.proto

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * OPACK serialization used by Apple's Companion link.
 *
 * Ported from pyatv (pyatv/support/opack.py, MIT licensed) so the byte level
 * encoding stays identical to a known working implementation.
 */
object Opack {

    fun pack(data: Any?): ByteArray {
        val objectList = ArrayList<ByteArray>()
        return packValue(data, objectList)
    }

    fun unpack(data: ByteArray): Any? = unpackValue(Cursor(data, 0), ArrayList()).also { }

    // ---------------------------------------------------------------- packing

    private fun packValue(data: Any?, objectList: MutableList<ByteArray>): ByteArray {
        val packed: ByteArray = when (data) {
            null -> byteArrayOf(0x04)
            is Boolean -> byteArrayOf(if (data) 1 else 2)
            is UUID -> byteArrayOf(0x05) + uuidBytes(data)
            is Byte, is Short, is Int, is Long -> packInt((data as Number).toLong())
            is Float -> byteArrayOf(0x36) + le(data.toDouble().toRawBits(), 8)
            is Double -> byteArrayOf(0x36) + le(data.toRawBits(), 8)
            is String -> packString(data)
            is ByteArray -> packBytes(data)
            is List<*> -> {
                val body = ByteArrayOutputStream()
                body.write(byteArrayOf((0xD0 + minOf(data.size, 0xF)).toByte()))
                data.forEach { body.write(packValue(it, objectList)) }
                if (data.size >= 0xF) body.write(0x03)
                body.toByteArray()
            }
            is Map<*, *> -> {
                val body = ByteArrayOutputStream()
                body.write(byteArrayOf((0xE0 + minOf(data.size, 0xF)).toByte()))
                data.forEach { (k, v) ->
                    body.write(packValue(k, objectList))
                    body.write(packValue(v, objectList))
                }
                if (data.size >= 0xF) body.write(0x03)
                body.toByteArray()
            }
            else -> throw IllegalArgumentException("cannot pack ${data.javaClass}")
        }

        val existing = objectList.indexOfFirst { it.contentEquals(packed) }
        if (existing >= 0) {
            return when {
                existing < 0x21 -> byteArrayOf((0xA0 + existing).toByte())
                existing <= 0xFF -> byteArrayOf(0xC1.toByte()) + le(existing.toLong(), 1)
                existing <= 0xFFFF -> byteArrayOf(0xC2.toByte()) + le(existing.toLong(), 2)
                else -> byteArrayOf(0xC3.toByte()) + le(existing.toLong(), 4)
            }
        }
        if (packed.size > 1) objectList.add(packed)
        return packed
    }

    private fun packInt(value: Long): ByteArray = when {
        value < 0x28 && value >= 0 -> byteArrayOf((value + 8).toByte())
        value <= 0xFF -> byteArrayOf(0x30) + le(value, 1)
        value <= 0xFFFF -> byteArrayOf(0x31) + le(value, 2)
        value <= 0xFFFFFFFFL -> byteArrayOf(0x32) + le(value, 4)
        else -> byteArrayOf(0x33) + le(value, 8)
    }

    private fun packString(value: String): ByteArray {
        val encoded = value.toByteArray(Charsets.UTF_8)
        return when {
            encoded.size <= 0x20 -> byteArrayOf((0x40 + encoded.size).toByte()) + encoded
            encoded.size <= 0xFF -> byteArrayOf(0x61) + le(encoded.size.toLong(), 1) + encoded
            encoded.size <= 0xFFFF -> byteArrayOf(0x62) + le(encoded.size.toLong(), 2) + encoded
            encoded.size <= 0xFFFFFF -> byteArrayOf(0x63) + le(encoded.size.toLong(), 3) + encoded
            else -> byteArrayOf(0x64) + le(encoded.size.toLong(), 4) + encoded
        }
    }

    private fun packBytes(value: ByteArray): ByteArray = when {
        value.size <= 0x20 -> byteArrayOf((0x70 + value.size).toByte()) + value
        value.size <= 0xFF -> byteArrayOf(0x91.toByte()) + le(value.size.toLong(), 1) + value
        value.size <= 0xFFFF -> byteArrayOf(0x92.toByte()) + le(value.size.toLong(), 2) + value
        else -> byteArrayOf(0x93.toByte()) + le(value.size.toLong(), 4) + value
    }

    private fun le(value: Long, size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) out[i] = ((value shr (8 * i)) and 0xFF).toByte()
        return out
    }

    private fun uuidBytes(uuid: UUID): ByteArray = ByteBuffer.allocate(16)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(uuid.mostSignificantBits)
        .putLong(uuid.leastSignificantBits)
        .array()

    // -------------------------------------------------------------- unpacking

    private class Cursor(val data: ByteArray, var pos: Int) {
        fun u8(): Int = data[pos++].toInt() and 0xFF
        fun peek(): Int = data[pos].toInt() and 0xFF
        fun take(n: Int): ByteArray {
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
        fun leInt(n: Int): Long {
            var v = 0L
            for (i in 0 until n) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
            pos += n
            return v
        }
    }

    private fun unpackValue(c: Cursor, objectList: MutableList<Any?>): Any? {
        val tag = c.u8()
        var addToList = true
        val value: Any? = when {
            tag == 0x01 -> { addToList = false; true }
            tag == 0x02 -> { addToList = false; false }
            tag == 0x04 -> { addToList = false; null }
            tag == 0x05 -> {
                val b = c.take(16)
                val bb = ByteBuffer.wrap(b)
                UUID(bb.long, bb.long)
            }
            tag == 0x06 -> c.leInt(8)
            tag in 0x08..0x2F -> { addToList = false; (tag - 8) }
            tag == 0x35 -> Float.fromBits(c.leInt(4).toInt()).toDouble()
            tag == 0x36 -> Double.fromBits(c.leInt(8))
            (tag and 0xF0) == 0x30 -> {
                val n = 1 shl (tag and 0xF)
                c.leInt(n)
            }
            tag in 0x40..0x60 -> String(c.take(tag - 0x40), Charsets.UTF_8)
            tag in 0x61..0x64 -> {
                val n = tag and 0xF
                val len = c.leInt(n).toInt()
                String(c.take(len), Charsets.UTF_8)
            }
            tag in 0x70..0x90 -> c.take(tag - 0x70)
            tag in 0x91..0x94 -> {
                val n = 1 shl ((tag and 0xF) - 1)
                val len = c.leInt(n).toInt()
                c.take(len)
            }
            (tag and 0xF0) == 0xD0 -> {
                addToList = false
                val count = tag and 0xF
                val out = ArrayList<Any?>()
                if (count == 0xF) {
                    while (c.peek() != 0x03) out.add(unpackValue(c, objectList))
                    c.pos++
                } else {
                    repeat(count) { out.add(unpackValue(c, objectList)) }
                }
                out
            }
            (tag and 0xE0) == 0xE0 -> {
                addToList = false
                val count = tag and 0xF
                val out = LinkedHashMap<Any?, Any?>()
                if (count == 0xF) {
                    while (c.peek() != 0x03) {
                        val k = unpackValue(c, objectList)
                        out[k] = unpackValue(c, objectList)
                    }
                    c.pos++
                } else {
                    repeat(count) {
                        val k = unpackValue(c, objectList)
                        out[k] = unpackValue(c, objectList)
                    }
                }
                out
            }
            tag in 0xA0..0xC0 -> { addToList = false; objectList[tag - 0xA0] }
            tag in 0xC1..0xC4 -> {
                addToList = false
                val len = tag - 0xC0
                objectList[c.leInt(len).toInt()]
            }
            else -> throw IllegalArgumentException("unknown opack tag 0x%02X".format(tag))
        }

        if (addToList && objectList.none { sameValue(it, value) }) objectList.add(value)
        return value
    }

    private fun sameValue(a: Any?, b: Any?): Boolean = when {
        a is ByteArray && b is ByteArray -> a.contentEquals(b)
        else -> a == b
    }
}
