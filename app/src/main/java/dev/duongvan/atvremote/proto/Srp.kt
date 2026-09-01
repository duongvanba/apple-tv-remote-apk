package dev.duongvan.atvremote.proto

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

/**
 * SRP-6a client for HomeKit pair-setup (3072 bit group, SHA-512, user
 * "Pair-Setup").
 *
 * The byte conversions deliberately mirror the srptools/pyatv behaviour:
 * integers are hashed using their *minimal* big-endian representation, while
 * only A and B inside u are padded to the width of N.
 */
class Srp(private val pin: String, privateKeyOverride: ByteArray? = null) {

    companion object {
        private const val PRIME_HEX =
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA6" +
            "3B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F2411" +
            "7C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08" +
            "CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D" +
            "04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D8760273" +
            "3EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
            "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF"

        private const val USERNAME = "Pair-Setup"

        val N: BigInteger = BigInteger(PRIME_HEX, 16)
        val G: BigInteger = BigInteger.valueOf(5)
        private val PAD_WIDTH = N.toMinimalBytes().size

        fun BigInteger.toMinimalBytes(): ByteArray {
            val length = maxOf((bitLength() + 7) / 8, 1)
            val raw = toByteArray()
            return when {
                raw.size == length -> raw
                raw.size > length -> raw.copyOfRange(raw.size - length, raw.size)
                else -> ByteArray(length - raw.size) + raw
            }
        }

        private fun pad(value: BigInteger): ByteArray {
            val raw = value.toMinimalBytes()
            return if (raw.size >= PAD_WIDTH) raw else ByteArray(PAD_WIDTH - raw.size) + raw
        }

        private fun digest(vararg parts: Any): ByteArray {
            val buffer = ByteArrayOutputStream()
            for (part in parts) {
                when (part) {
                    is BigInteger -> buffer.write(part.toMinimalBytes())
                    is ByteArray -> buffer.write(part)
                    is String -> buffer.write(part.toByteArray(Charsets.UTF_8))
                    else -> throw IllegalArgumentException("unsupported hash input")
                }
            }
            return MessageDigest.getInstance("SHA-512").digest(buffer.toByteArray())
        }

        private fun digestInt(vararg parts: Any): BigInteger =
            BigInteger(1, digest(*parts))
    }

    private val k: BigInteger = digestInt(N, pad(G))
    private var a: BigInteger = BigInteger.ZERO
    private var publicA: BigInteger = BigInteger.ZERO

    /** Client public value A, always exactly [PAD_WIDTH] bytes. */
    val clientPublic: ByteArray

    init {
        if (privateKeyOverride != null) {
            a = BigInteger(1, privateKeyOverride)
            publicA = G.modPow(a, N)
        } else {
            // Regenerating until A is full width keeps the "minimal bytes"
            // encoding used for hashing identical to what is put on the wire.
            do {
                a = BigInteger(1, Crypto.randomBytes(32))
                publicA = G.modPow(a, N)
            } while (publicA.toMinimalBytes().size != PAD_WIDTH)
        }
        clientPublic = publicA.toMinimalBytes()
    }

    lateinit var sessionKey: ByteArray
        private set

    /** Returns the client proof M1 for the given device salt and public key B. */
    fun proof(salt: ByteArray, serverPublicKey: ByteArray): ByteArray {
        val serverPublic = BigInteger(1, serverPublicKey)
        require(serverPublic.mod(N) != BigInteger.ZERO) { "invalid server public key" }

        val x = digestInt(salt, digest("$USERNAME:$pin"))
        val u = digestInt(pad(publicA), pad(serverPublic))
        val v = G.modPow(x, N)
        val premaster = serverPublic.subtract(k.multiply(v)).modPow(a.add(u.multiply(x)), N)

        sessionKey = digest(premaster)

        val hashN = digestInt(N)
        val hashG = digestInt(G)
        return digest(hashN.xor(hashG), digestInt(USERNAME), salt, publicA, serverPublic, sessionKey)
    }

    /** Expected device proof M2, used to sanity check the response. */
    fun expectedServerProof(clientProof: ByteArray): ByteArray =
        digest(publicA, clientProof, sessionKey)
}
