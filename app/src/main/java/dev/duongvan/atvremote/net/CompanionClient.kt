package dev.duongvan.atvremote.net

import android.util.Log
import dev.duongvan.atvremote.data.Credentials
import dev.duongvan.atvremote.proto.Crypto
import dev.duongvan.atvremote.proto.Opack
import dev.duongvan.atvremote.proto.Srp
import dev.duongvan.atvremote.proto.Tlv8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class CompanionException(message: String) : Exception(message)

enum class HidCommand(val value: Int) {
    Up(1), Down(2), Left(3), Right(4), Menu(5), Select(6), Home(7),
    VolumeUp(8), VolumeDown(9), Siri(10), Screensaver(11), Sleep(12),
    Wake(13), PlayPause(14), ChannelIncrement(15), ChannelDecrement(16),
    Guide(17), PageUp(18), PageDown(19)
}

enum class TouchPhase(val value: Int) {
    Press(1), Hold(3), Release(4), Click(5)
}

data class AppEntry(val bundleId: String, val name: String)

/**
 * Companion link client: pairing, encrypted session setup and the commands the
 * remote UI needs.
 */
class CompanionClient(
    private val host: String,
    private val port: Int,
    private val controllerName: String
) {
    companion object {
        private const val TAG = "CompanionClient"
        private const val TOUCH_SIZE = 1000.0
        private const val MSG_EVENT = 1
        private const val MSG_REQUEST = 2
        private const val MSG_RESPONSE = 3
        private const val REQUEST_TIMEOUT_MS = 8000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Map<*, *>>>()

    private var connection: CompanionConnection? = null
    private var readerJob: Job? = null
    private var xid = Random.nextInt(1, 60000)
    private var touchBaseNanos = System.nanoTime()
    private var sessionId: Long = 0

    @Volatile
    var isReady: Boolean = false
        private set

    // Pair-setup state
    private var srp: Srp? = null
    private var setupSalt: ByteArray? = null
    private var setupServerKey: ByteArray? = null
    private val signingSeed: ByteArray = Crypto.randomBytes(32)
    private val pairingId: ByteArray = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)

    // ------------------------------------------------------------- connection

    private suspend fun openSocket() = withContext(Dispatchers.IO) {
        close()
        val newConnection = CompanionConnection(host, port)
        newConnection.connect()
        connection = newConnection
        readerJob = scope.launch { readLoop(newConnection) }
    }

    private fun readLoop(active: CompanionConnection) {
        while (true) {
            val frame = try {
                active.readFrame()
            } catch (t: Throwable) {
                Log.w(TAG, "read failed", t)
                null
            } ?: break
            try {
                handleFrame(frame.first, frame.second)
            } catch (t: Throwable) {
                Log.w(TAG, "frame handling failed", t)
            }
        }
        if (connection === active) {
            isReady = false
            failAllPending("mất kết nối tới thiết bị")
        }
    }

    private fun failAllPending(reason: String) {
        pending.keys.toList().forEach { key ->
            pending.remove(key)?.completeExceptionally(CompanionException(reason))
        }
    }

    private fun handleFrame(frameType: Int, payload: ByteArray) {
        if (payload.isEmpty()) return
        val decoded = Opack.unpack(payload) as? Map<*, *> ?: return
        when (frameType) {
            FrameType.PS_START, FrameType.PS_NEXT, FrameType.PV_START, FrameType.PV_NEXT ->
                pending.remove("auth:$frameType")?.complete(decoded)
            FrameType.E_OPACK, FrameType.U_OPACK, FrameType.P_OPACK -> {
                val type = (decoded["_t"] as? Number)?.toInt()
                if (type == MSG_RESPONSE) {
                    val id = (decoded["_x"] as? Number)?.toLong()
                    pending.remove("xid:$id")?.complete(decoded)
                }
            }
        }
    }

    fun close() {
        isReady = false
        readerJob?.cancel()
        readerJob = null
        connection?.close()
        connection = null
        failAllPending("kết nối đã đóng")
    }

    // -------------------------------------------------------------- messaging

    private suspend fun exchange(
        frameType: Int,
        message: MutableMap<String, Any?>,
        key: String
    ): Map<*, *> {
        val active = connection ?: throw CompanionException("chưa kết nối")
        val deferred = CompletableDeferred<Map<*, *>>()
        pending[key] = deferred
        try {
            withContext(Dispatchers.IO) { active.send(frameType, Opack.pack(message)) }
            val response = withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
            (response["_em"] as? String)?.let { throw CompanionException("thiết bị báo lỗi: $it") }
            return response
        } finally {
            pending.remove(key)
        }
    }

    private suspend fun exchangeAuth(frameType: Int, message: MutableMap<String, Any?>): Map<*, *> {
        val responseFrame = when (frameType) {
            FrameType.PS_START -> FrameType.PS_NEXT
            FrameType.PV_START -> FrameType.PV_NEXT
            else -> frameType
        }
        return exchange(frameType, message, "auth:$responseFrame")
    }

    private suspend fun request(identifier: String, content: Map<String, Any?>): Map<*, *> {
        val id = nextXid()
        val message = linkedMapOf<String, Any?>(
            "_i" to identifier,
            "_t" to MSG_REQUEST,
            "_c" to content,
            "_x" to id
        )
        return exchange(FrameType.E_OPACK, message, "xid:$id")
    }

    private suspend fun event(identifier: String, content: Map<String, Any?>) {
        val active = connection ?: throw CompanionException("chưa kết nối")
        val message = linkedMapOf<String, Any?>(
            "_i" to identifier,
            "_t" to MSG_EVENT,
            "_c" to content,
            "_x" to nextXid()
        )
        withContext(Dispatchers.IO) { active.send(FrameType.E_OPACK, Opack.pack(message)) }
    }

    @Synchronized
    private fun nextXid(): Long = (++xid).toLong()

    // ------------------------------------------------------------ pair setup

    /** Opens a connection and asks the device to display a pairing PIN. */
    suspend fun startPairing() {
        openSocket()
        val message = linkedMapOf<String, Any?>(
            "_pd" to Tlv8.write(
                linkedMapOf(
                    Tlv8.METHOD to byteArrayOf(0x00),
                    Tlv8.SEQ_NO to byteArrayOf(0x01)
                )
            ),
            "_pwTy" to 1
        )
        val tlv = pairingData(exchangeAuth(FrameType.PS_START, message))
        setupSalt = tlv[Tlv8.SALT] ?: throw CompanionException("thiếu salt từ thiết bị")
        setupServerKey = tlv[Tlv8.PUBLIC_KEY] ?: throw CompanionException("thiếu public key")
    }

    /** Completes pair-setup with the PIN shown on screen. */
    suspend fun finishPairing(pin: String): Credentials {
        val salt = setupSalt ?: throw CompanionException("chưa bắt đầu ghép nối")
        val serverKey = setupServerKey ?: throw CompanionException("chưa bắt đầu ghép nối")

        val client = Srp(pin.padStart(4, '0'))
        srp = client
        val proof = client.proof(salt, serverKey)

        val proofMessage = linkedMapOf<String, Any?>(
            "_pd" to Tlv8.write(
                linkedMapOf(
                    Tlv8.SEQ_NO to byteArrayOf(0x03),
                    Tlv8.PUBLIC_KEY to client.clientPublic,
                    Tlv8.PROOF to proof
                )
            ),
            "_pwTy" to 1
        )
        val proofTlv = pairingData(exchangeAuth(FrameType.PS_NEXT, proofMessage))
        proofTlv[Tlv8.PROOF]?.let { deviceProof ->
            if (!deviceProof.contentEquals(client.expectedServerProof(proof))) {
                Log.w(TAG, "device proof mismatch, continuing anyway")
            }
        }

        val sessionKey = client.sessionKey
        val deviceX = Crypto.hkdf(
            "Pair-Setup-Controller-Sign-Salt",
            "Pair-Setup-Controller-Sign-Info",
            sessionKey
        )
        val encryptKey = Crypto.hkdf(
            "Pair-Setup-Encrypt-Salt",
            "Pair-Setup-Encrypt-Info",
            sessionKey
        )
        val publicKey = Crypto.ed25519PublicKey(signingSeed)
        val signature = Crypto.ed25519Sign(signingSeed, deviceX + pairingId + publicKey)

        val payload = Tlv8.write(
            linkedMapOf(
                Tlv8.IDENTIFIER to pairingId,
                Tlv8.PUBLIC_KEY to publicKey,
                Tlv8.SIGNATURE to signature,
                Tlv8.NAME to Opack.pack(linkedMapOf("name" to controllerName))
            )
        )
        val cipher = Crypto.ChachaCipher(encryptKey, encryptKey, nonceLength = 8)
        val encrypted = cipher.encrypt(payload, nonce = "PS-Msg05".toByteArray(Charsets.UTF_8))

        val finalMessage = linkedMapOf<String, Any?>(
            "_pd" to Tlv8.write(
                linkedMapOf(
                    Tlv8.SEQ_NO to byteArrayOf(0x05),
                    Tlv8.ENCRYPTED_DATA to encrypted
                )
            ),
            "_pwTy" to 1
        )
        val resultTlv = pairingData(exchangeAuth(FrameType.PS_NEXT, finalMessage))
        val encryptedResult = resultTlv[Tlv8.ENCRYPTED_DATA]
            ?: throw CompanionException("thiếu dữ liệu ghép nối trả về")

        val decrypted = try {
            cipher.decrypt(encryptedResult, nonce = "PS-Msg06".toByteArray(Charsets.UTF_8))
        } catch (t: Throwable) {
            throw CompanionException("không giải mã được phản hồi ghép nối")
        }
        val finalTlv = Tlv8.read(decrypted)
        val atvId = finalTlv[Tlv8.IDENTIFIER] ?: throw CompanionException("thiếu định danh thiết bị")
        val atvPublicKey = finalTlv[Tlv8.PUBLIC_KEY] ?: throw CompanionException("thiếu khoá thiết bị")

        close()
        return Credentials(atvPublicKey, signingSeed, atvId, pairingId)
    }

    private fun pairingData(response: Map<*, *>): Map<Int, ByteArray> {
        val raw = response["_pd"] as? ByteArray
            ?: throw CompanionException("phản hồi không có dữ liệu ghép nối")
        val tlv = Tlv8.read(raw)
        Tlv8.errorMessage(tlv)?.let { throw CompanionException(it) }
        return tlv
    }

    // ----------------------------------------------------------- pair verify

    /** Connects using stored credentials and starts an encrypted session. */
    suspend fun connect(credentials: Credentials) {
        openSocket()

        val keyPair = Crypto.X25519KeyPair()
        val startMessage = linkedMapOf<String, Any?>(
            "_pd" to Tlv8.write(
                linkedMapOf(
                    Tlv8.SEQ_NO to byteArrayOf(0x01),
                    Tlv8.PUBLIC_KEY to keyPair.publicKey
                )
            ),
            "_auTy" to 4
        )
        val tlv = pairingData(exchangeAuth(FrameType.PV_START, startMessage))
        val serverPublicKey = tlv[Tlv8.PUBLIC_KEY] ?: throw CompanionException("thiếu khoá phiên")
        val encrypted = tlv[Tlv8.ENCRYPTED_DATA] ?: throw CompanionException("thiếu dữ liệu mã hoá")

        val shared = keyPair.sharedSecret(serverPublicKey)
        val sessionKey = Crypto.hkdf("Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info", shared)
        val cipher = Crypto.ChachaCipher(sessionKey, sessionKey, nonceLength = 8)

        val decrypted = try {
            cipher.decrypt(encrypted, nonce = "PV-Msg02".toByteArray(Charsets.UTF_8))
        } catch (t: Throwable) {
            throw CompanionException("xác thực thất bại, hãy ghép nối lại")
        }
        val deviceTlv = Tlv8.read(decrypted)
        val identifier = deviceTlv[Tlv8.IDENTIFIER] ?: throw CompanionException("thiếu định danh")
        val signature = deviceTlv[Tlv8.SIGNATURE] ?: throw CompanionException("thiếu chữ ký")

        if (!identifier.contentEquals(credentials.atvId)) {
            throw CompanionException("thiết bị trả về định danh khác, hãy ghép nối lại")
        }
        val signedInfo = serverPublicKey + identifier + keyPair.publicKey
        if (!Crypto.ed25519Verify(credentials.ltpk, signedInfo, signature)) {
            throw CompanionException("chữ ký thiết bị không hợp lệ")
        }

        val deviceInfo = keyPair.publicKey + credentials.clientId + serverPublicKey
        val ourSignature = Crypto.ed25519Sign(credentials.ltsk, deviceInfo)
        val replyPayload = Tlv8.write(
            linkedMapOf(
                Tlv8.IDENTIFIER to credentials.clientId,
                Tlv8.SIGNATURE to ourSignature
            )
        )
        val replyEncrypted = cipher.encrypt(
            replyPayload,
            nonce = "PV-Msg03".toByteArray(Charsets.UTF_8)
        )
        val nextMessage = linkedMapOf<String, Any?>(
            "_pd" to Tlv8.write(
                linkedMapOf(
                    Tlv8.SEQ_NO to byteArrayOf(0x03),
                    Tlv8.ENCRYPTED_DATA to replyEncrypted
                )
            )
        )
        pairingData(exchangeAuth(FrameType.PV_NEXT, nextMessage))

        val outputKey = Crypto.hkdf("", "ClientEncrypt-main", shared)
        val inputKey = Crypto.hkdf("", "ServerEncrypt-main", shared)
        connection?.enableEncryption(outputKey, inputKey)

        startSession(credentials)
        isReady = true
    }

    private suspend fun startSession(credentials: Credentials) {
        val deviceIdentifier = credentials.clientId
            .toString(Charsets.UTF_8)
            .filter { it.isLetterOrDigit() }
            .take(12)
            .lowercase()

        request(
            "_systemInfo",
            linkedMapOf(
                "_bf" to 0,
                "_cf" to 512,
                "_clFl" to 128,
                "_i" to deviceIdentifier,
                "_idsID" to credentials.clientId,
                "_pubID" to deviceIdentifier,
                "_sf" to 256,
                "_sv" to "170.18",
                "model" to "iPhone14,3",
                "name" to controllerName
            )
        )

        touchBaseNanos = System.nanoTime()
        request(
            "_touchStart",
            linkedMapOf("_height" to TOUCH_SIZE, "_tFl" to 0, "_width" to TOUCH_SIZE)
        )

        val localSid = Random.nextLong(0, 0xFFFFFFFFL)
        val sessionResponse = request(
            "_sessionStart",
            linkedMapOf("_srvT" to "com.apple.tvremoteservices", "_sid" to localSid)
        )
        val remoteSid = ((sessionResponse["_c"] as? Map<*, *>)?.get("_sid") as? Number)?.toLong() ?: 0L
        sessionId = (remoteSid shl 32) or localSid

        runCatching { request("TVRCSessionStart", linkedMapOf("ProtocolVersionKey" to "1.2")) }
    }

    suspend fun disconnect() {
        runCatching {
            request(
                "_sessionStop",
                linkedMapOf("_srvT" to "com.apple.tvremoteservices", "_sid" to sessionId)
            )
            request("_touchStop", linkedMapOf("_i" to 1))
        }
        close()
    }

    // -------------------------------------------------------------- commands

    suspend fun pressButton(command: HidCommand) {
        request("_hidC", linkedMapOf("_hBtS" to 1, "_hidC" to command.value))
        request("_hidC", linkedMapOf("_hBtS" to 2, "_hidC" to command.value))
    }

    suspend fun holdButton(command: HidCommand, durationMs: Long) {
        request("_hidC", linkedMapOf("_hBtS" to 1, "_hidC" to command.value))
        kotlinx.coroutines.delay(durationMs)
        request("_hidC", linkedMapOf("_hBtS" to 2, "_hidC" to command.value))
    }

    /** Sends a raw touchpad sample; coordinates are in the range 0..1000. */
    suspend fun touch(x: Int, y: Int, phase: TouchPhase) {
        event(
            "_hidT",
            linkedMapOf(
                "_ns" to (System.nanoTime() - touchBaseNanos),
                "_tFg" to 1,
                "_cx" to x.coerceIn(0, TOUCH_SIZE.toInt()),
                "_tPh" to phase.value,
                "_cy" to y.coerceIn(0, TOUCH_SIZE.toInt())
            )
        )
    }

    suspend fun appList(): List<AppEntry> {
        val response = request("FetchLaunchableApplicationsEvent", emptyMap())
        val content = response["_c"] as? Map<*, *> ?: return emptyList()
        return content.mapNotNull { (bundleId, name) ->
            val id = bundleId as? String ?: return@mapNotNull null
            AppEntry(id, name as? String ?: id)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun launchApp(bundleId: String) {
        request("_launchApp", linkedMapOf("_bundleID" to bundleId))
    }

    suspend fun getVolume(): Double? {
        val response = runCatching { request("_mcc", linkedMapOf("_mcc" to 5)) }.getOrNull()
        val content = response?.get("_c") as? Map<*, *> ?: return null
        return (content["_vol"] as? Number)?.toDouble()
    }

    suspend fun setVolume(level: Double) {
        request("_mcc", linkedMapOf("_mcc" to 6, "_vol" to level.coerceIn(0.0, 1.0)))
    }
}
