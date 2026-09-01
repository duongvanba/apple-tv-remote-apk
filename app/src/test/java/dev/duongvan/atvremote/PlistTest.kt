package dev.duongvan.atvremote

import dev.duongvan.atvremote.data.fromHex
import dev.duongvan.atvremote.data.toHex
import dev.duongvan.atvremote.proto.BinaryPlist
import dev.duongvan.atvremote.proto.RtiPayloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RTI payloads are NSKeyedArchiver blobs, so the binary plist writer is
 * checked against output produced by CPython's plistlib through pyatv's own
 * payload builders.
 */
class PlistTest {

    @Test
    fun `insert text stays byte compatible with pyatv`() {
        val uuid = "0102030405060708090a0b0c0d0e0f10".fromHex()
        val expected =
            "62706c6973743030d4010203040506070a582476657273696f6e59246172636869766572" +
            "5424746f7058246f626a6563747312000186a05f10105254494b65796564417263686976" +
            "6572d108095e746578744f7065726174696f6e738001a80b0c1317181e222555246e756c" +
            "6cd30d0e0f1011125e6b6579626f6172644f75747075745624636c6173735f1011746172" +
            "67657453657373696f6e55554944800280078005d2140e15165d696e73657274696f6e54" +
            "657874800380045568656c6c6fd2191a1b1c5a24636c6173736e616d655824636c617373" +
            "65735f101054494b6579626f6172644f7574707574a21b1d584e534f626a656374d21f0e" +
            "20215c4e532e7575696462797465734f10100102030405060708090a0b0c0d0e0f108006" +
            "d2191a2324564e5355554944a2231dd2191a26275f1011525449546578744f7065726174" +
            "696f6e73a2261d00080011001a0024002900320037004a004d005c005e0067006d007400" +
            "83008a009e00a000a200a400a900b700b900bb00c100c600d100da00ed00f000f900fe01" +
            "0b011e01200125012c012f01340148000000000000020100000000000000280000000000" +
            "000000000000000000014b"
        assertEquals(expected, RtiPayloads.insertText(uuid, "hello").toHex())
    }

    @Test
    fun `insert unicode text stays byte compatible with pyatv`() {
        val uuid = "0102030405060708090a0b0c0d0e0f10".fromHex()
        val expected =
            "62706c6973743030d4010203040506070a582476657273696f6e59246172636869766572" +
            "5424746f7058246f626a6563747312000186a05f10105254494b65796564417263686976" +
            "6572d108095e746578744f7065726174696f6e738001a80b0c1317181e222555246e756c" +
            "6cd30d0e0f1011125e6b6579626f6172644f75747075745624636c6173735f1011746172" +
            "67657453657373696f6e55554944800280078005d2140e15165d696e73657274696f6e54" +
            "657874800380046d005400ec006d0020006b00691ebf006d0020007000680069006dd219" +
            "1a1b1c5a24636c6173736e616d655824636c61737365735f101054494b6579626f617264" +
            "4f7574707574a21b1d584e534f626a656374d21f0e20215c4e532e757569646279746573" +
            "4f10100102030405060708090a0b0c0d0e0f108006d2191a2324564e5355554944a2231d" +
            "d2191a26275f1011525449546578744f7065726174696f6e73a2261d00080011001a0024" +
            "002900320037004a004d005c005e0067006d00740083008a009e00a000a200a400a900b7" +
            "00b900bb00d600db00e600ef01020105010e0113012001330135013a014101440149015d" +
            "0000000000000201000000000000002800000000000000000000000000000160"
        assertEquals(expected, RtiPayloads.insertText(uuid, "Tìm kiếm phim").toHex())
    }

    @Test
    fun `clear text stays byte compatible with pyatv`() {
        val uuid = "0102030405060708090a0b0c0d0e0f10".fromHex()
        val expected =
            "62706c6973743030d4010203040506070a582476657273696f6e59246172636869766572" +
            "5424746f7058246f626a6563747312000186a05f10105254494b65796564417263686976" +
            "6572d108095e746578744f7065726174696f6e738001a80b0c15171d1e222555246e756c" +
            "6cd40d0e0f10111213145624636c6173735f101174617267657453657373696f6e555549" +
            "445e6b6579626f6172644f75747075745c74657874546f41737365727480078005800280" +
            "04d10d168003d218191a1b5a24636c6173736e616d655824636c61737365735f10105449" +
            "4b6579626f6172644f7574707574a21a1c584e534f626a65637450d21f0d20215c4e532e" +
            "7575696462797465734f10100102030405060708090a0b0c0d0e0f108006d21819232456" +
            "4e5355554944a2231cd2181926275f1011525449546578744f7065726174696f6e73a226" +
            "1c00080011001a0024002900320037004a004d005c005e0067006d0076007d009100a000" +
            "ad00af00b100b300b500b800ba00bf00ca00d300e600e900f200f300f801050118011a01" +
            "1f01260129012e0142000000000000020100000000000000280000000000000000000000" +
            "0000000145"
        assertEquals(expected, RtiPayloads.clearText(uuid).toHex())
    }

    @Test
    fun `session uuid can be read back from the archive`() {
        val uuid = "0102030405060708090a0b0c0d0e0f10".fromHex()
        val archive = BinaryPlist.encode(
            linkedMapOf<String, Any?>(
                "\$version" to 100000,
                "\$archiver" to "RTIKeyedArchiver",
                "\$top" to linkedMapOf<String, Any?>("sessionUUID" to BinaryPlist.Uid(1)),
                "\$objects" to listOf("\$null", uuid)
            )
        )
        val readBack = BinaryPlist.archiveProperty(archive, "sessionUUID") as ByteArray
        assertTrue(readBack.contentEquals(uuid))
    }

    @Test
    fun `plist round trips nested values`() {
        val value = linkedMapOf<String, Any?>(
            "text" to "xin chào",
            "count" to 100000,
            "raw" to byteArrayOf(1, 2, 3),
            "list" to listOf("a", "a", 7),
            "nested" to linkedMapOf<String, Any?>("uid" to BinaryPlist.Uid(3))
        )
        val decoded = BinaryPlist.decode(BinaryPlist.encode(value)) as Map<*, *>
        assertEquals("xin chào", decoded["text"])
        assertEquals(100000L, decoded["count"])
        assertEquals(listOf("a", "a", 7L), decoded["list"])
        assertTrue((decoded["raw"] as ByteArray).contentEquals(byteArrayOf(1, 2, 3)))
        val nested = decoded["nested"] as Map<*, *>
        assertEquals(3, (nested["uid"] as BinaryPlist.Uid).value)
    }
}
