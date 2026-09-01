package dev.duongvan.atvremote.proto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

object Crypto {

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /** HKDF-SHA512 producing a 32 byte key, matching pyatv's hkdf_expand(). */
    fun hkdf(salt: String, info: String, ikm: ByteArray): ByteArray {
        val generator = HKDFBytesGenerator(SHA512Digest())
        generator.init(
            HKDFParameters(
                ikm,
                salt.toByteArray(Charsets.UTF_8),
                info.toByteArray(Charsets.UTF_8)
            )
        )
        val out = ByteArray(32)
        generator.generateBytes(out, 0, out.size)
        return out
    }

    fun ed25519Sign(privateSeed: ByteArray, message: ByteArray): ByteArray {
        val key = Ed25519PrivateKeyParameters(privateSeed, 0)
        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return try {
            val key = Ed25519PublicKeyParameters(publicKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, key)
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (t: Throwable) {
            false
        }
    }

    fun ed25519PublicKey(privateSeed: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(privateSeed, 0).generatePublicKey().encoded

    class X25519KeyPair {
        private val privateKey = X25519PrivateKeyParameters(randomBytes(32), 0)
        val publicKey: ByteArray = privateKey.generatePublicKey().encoded

        fun sharedSecret(peerPublicKey: ByteArray): ByteArray {
            val agreement = X25519Agreement()
            agreement.init(privateKey)
            val out = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), out, 0)
            return out
        }
    }

    /**
     * ChaCha20-Poly1305 layer with separate in/out keys and running counters,
     * mirroring pyatv's Chacha20Cipher.
     */
    class ChachaCipher(
        private val outKey: ByteArray,
        private val inKey: ByteArray,
        private val nonceLength: Int = 8
    ) {
        private var outCounter = 0L
        private var inCounter = 0L

        fun encrypt(data: ByteArray, nonce: ByteArray? = null, aad: ByteArray? = null): ByteArray {
            val actual = nonce?.let { padNonce(it) } ?: padNonce(counterNonce(outCounter++))
            return process(true, outKey, actual, data, aad)
        }

        fun decrypt(data: ByteArray, nonce: ByteArray? = null, aad: ByteArray? = null): ByteArray {
            val actual = nonce?.let { padNonce(it) } ?: padNonce(counterNonce(inCounter++))
            return process(false, inKey, actual, data, aad)
        }

        private fun counterNonce(counter: Long): ByteArray {
            val out = ByteArray(nonceLength)
            for (i in 0 until minOf(nonceLength, 8)) {
                out[i] = ((counter shr (8 * i)) and 0xFF).toByte()
            }
            return out
        }

        private fun padNonce(nonce: ByteArray): ByteArray =
            if (nonce.size == 12) nonce else ByteArray(12 - nonce.size) + nonce

        private fun process(
            forEncryption: Boolean,
            key: ByteArray,
            nonce: ByteArray,
            data: ByteArray,
            aad: ByteArray?
        ): ByteArray {
            val engine = ChaCha20Poly1305()
            engine.init(forEncryption, AEADParameters(KeyParameter(key), 128, nonce, aad))
            val out = ByteArray(engine.getOutputSize(data.size))
            var len = engine.processBytes(data, 0, data.size, out, 0)
            len += engine.doFinal(out, len)
            return if (len == out.size) out else out.copyOf(len)
        }
    }
}
