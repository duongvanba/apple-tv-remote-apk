package dev.duongvan.atvremote

import dev.duongvan.atvremote.data.fromHex
import dev.duongvan.atvremote.data.toHex
import dev.duongvan.atvremote.proto.Opack
import dev.duongvan.atvremote.proto.Srp
import dev.duongvan.atvremote.proto.Tlv8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference vectors produced with pyatv's own opack implementation and with the
 * srptools math pyatv relies on, so the Kotlin port stays byte compatible.
 */
class ProtocolTest {

    @Test
    fun `opack matches pyatv vectors`() {
        val cases = listOf(
            linkedMapOf<String, Any?>(
                "_i" to "_hidC",
                "_t" to 2,
                "_c" to linkedMapOf<String, Any?>("_hBtS" to 1, "_hidC" to 6),
                "_x" to 40000
            ) to "e4425f69455f68696443425f740a425f63e2455f6842745309a10e425f7831409c",

            linkedMapOf<String, Any?>(
                "_i" to "_hidT",
                "_t" to 1,
                "_c" to linkedMapOf<String, Any?>(
                    "_ns" to 123456789,
                    "_tFg" to 1,
                    "_cx" to 500,
                    "_tPh" to 3,
                    "_cy" to 500
                ),
                "_x" to 7
            ) to "e4425f69455f68696454425f7409425f63e5435f6e733215cd5b07445f74466709" +
                "435f637831f401445f7450680b435f6379a8425f780f",

            linkedMapOf<String, Any?>(
                "_i" to "_sessionStart",
                "_t" to 2,
                "_c" to linkedMapOf<String, Any?>(
                    "_srvT" to "com.apple.tvremoteservices",
                    "_sid" to 3735928559L
                ),
                "_x" to 9
            ) to "e4425f694d5f73657373696f6e5374617274425f740a425f63e2455f73727654" +
                "5a636f6d2e6170706c652e747672656d6f74657365727669636573445f73696432efbeadde425f7811",

            linkedMapOf<String, Any?>(
                "_i" to "_touchStart",
                "_t" to 2,
                "_c" to linkedMapOf<String, Any?>(
                    "_height" to 1000.0,
                    "_tFl" to 0,
                    "_width" to 1000.0
                ),
                "_x" to 1
            ) to "e4425f694b5f746f7563685374617274425f740a425f63e3475f6865696768743600" +
                "00000000408f40445f74466c08465f7769647468a5425f7809",

            linkedMapOf<String, Any?>(
                "_pd" to ByteArray(20) { it.toByte() },
                "_pwTy" to 1
            ) to "e2435f706484000102030405060708090a0b0c0d0e0f10111213455f7077547909",

            linkedMapOf<String, Any?>(
                "one" to "same",
                "two" to "same",
                "three" to listOf("same", "same")
            ) to "e3436f6e654473616d654374776fa1457468726565d2a1a1"
        )

        for ((value, expected) in cases) {
            assertEquals(expected, Opack.pack(value).toHex())
        }
    }

    @Test
    fun `opack round trips`() {
        val value = linkedMapOf<String, Any?>(
            "flag" to true,
            "off" to false,
            "nothing" to null,
            "list" to listOf(1, 2, "x"),
            "nested" to linkedMapOf<String, Any?>("k" to byteArrayOf(1, 2)),
            "long" to "x".repeat(40)
        )
        val decoded = Opack.unpack(Opack.pack(value)) as Map<*, *>
        assertEquals(true, decoded["flag"])
        assertEquals(false, decoded["off"])
        assertEquals(null, decoded["nothing"])
        assertEquals(listOf(1, 2, "x"), decoded["list"])
        assertEquals("x".repeat(40), decoded["long"])
        val nested = decoded["nested"] as Map<*, *>
        assertTrue((nested["k"] as ByteArray).contentEquals(byteArrayOf(1, 2)))
    }

    @Test
    fun `srp matches srptools vectors`() {
        val salt = "0102030405060708090a0b0c0d0e0f10".fromHex()
        val privateKey = "11".repeat(32).fromHex()
        val serverPublic = (
            "7cf2e5730cdea22f7c2f6e8fb926ff738464b20ec61a5b8a1c83f4facecdae30" +
            "6f29a2b768522d5cf0f367747f30ce39c74863278fae6c27e17ce9e30b6ccbd9"
            ).repeat(6).fromHex()

        val srp = Srp("1234", privateKey)
        assertEquals(384, srp.clientPublic.size)
        val proof = srp.proof(salt, serverPublic)

        assertEquals(
            "51760ebc7f3458c73e8873d311daf675aa50f5d3ab692edfaa50506b155e8d4a" +
                "7045e44df26925b5ccc150b9666a009003e8cdef3eff655c84dede99661a30b1",
            proof.toHex()
        )
        assertEquals(
            "e462a191ecdd7c496e5f40c851064fb9c0f97873713447a8d4b89aad709689c7" +
                "c37d2d98b02d2df966aa0993d163c57ae463c0ace00a76c7723d4bb2c2ca7b91",
            srp.sessionKey.toHex()
        )
        assertEquals(
            "5295379517605a631a7e4430c04fa1763e46c069fb3f6f3a7215a5c8efcab1e1" +
                "106d52943dfc9111b9be29f464798105e0824bb7f31afff003764f5051bbeabc",
            srp.expectedServerProof(proof).toHex()
        )
    }

    @Test
    fun `tlv8 splits long values`() {
        val value = ByteArray(300) { (it % 251).toByte() }
        val encoded = Tlv8.write(linkedMapOf(Tlv8.PUBLIC_KEY to value))
        assertEquals(300 + 4, encoded.size)
        val decoded = Tlv8.read(encoded)
        assertTrue(decoded.getValue(Tlv8.PUBLIC_KEY).contentEquals(value))
    }
}
