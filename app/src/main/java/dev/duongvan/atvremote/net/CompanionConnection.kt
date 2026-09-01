package dev.duongvan.atvremote.net

import dev.duongvan.atvremote.proto.Crypto
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

object FrameType {
    const val NO_OP = 1
    const val PS_START = 3
    const val PS_NEXT = 4
    const val PV_START = 5
    const val PV_NEXT = 6
    const val U_OPACK = 7
    const val E_OPACK = 8
    const val P_OPACK = 9
}

/** Raw framed TCP connection towards the Companion link port. */
class CompanionConnection(private val host: String, private val port: Int) {

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var cipher: Crypto.ChachaCipher? = null

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect(timeoutMs: Int = 5000) {
        val newSocket = Socket()
        newSocket.tcpNoDelay = true
        newSocket.connect(InetSocketAddress(host, port), timeoutMs)
        socket = newSocket
        input = DataInputStream(newSocket.getInputStream())
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        input = null
        cipher = null
    }

    fun enableEncryption(outputKey: ByteArray, inputKey: ByteArray) {
        cipher = Crypto.ChachaCipher(outputKey, inputKey, nonceLength = 12)
    }

    @Synchronized
    fun send(frameType: Int, payload: ByteArray) {
        val stream = socket?.getOutputStream() ?: throw IOException("not connected")
        val activeCipher = cipher
        val length = if (activeCipher != null && payload.isNotEmpty()) payload.size + 16 else payload.size
        val header = byteArrayOf(
            frameType.toByte(),
            ((length shr 16) and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        )
        val body = if (activeCipher != null && payload.isNotEmpty()) {
            activeCipher.encrypt(payload, aad = header)
        } else {
            payload
        }
        stream.write(header + body)
        stream.flush()
    }

    /** Blocking read of the next frame. Returns null when the peer closed. */
    fun readFrame(): Pair<Int, ByteArray>? {
        val stream = input ?: return null
        val header = ByteArray(4)
        try {
            stream.readFully(header)
        } catch (_: IOException) {
            return null
        }
        val length = ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        val body = ByteArray(length)
        if (length > 0) {
            try {
                stream.readFully(body)
            } catch (_: IOException) {
                return null
            }
        }
        val activeCipher = cipher
        val payload = if (activeCipher != null && body.isNotEmpty()) {
            activeCipher.decrypt(body, aad = header)
        } else {
            body
        }
        return (header[0].toInt() and 0xFF) to payload
    }
}
