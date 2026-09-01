package dev.duongvan.atvremote.proto

import dev.duongvan.atvremote.proto.BinaryPlist.Uid

/**
 * NSKeyedArchiver payloads for the tvOS remote text input (RTI) service.
 *
 * Rather than implementing a general archiver, the object graphs are laid out
 * by hand exactly like pyatv does, with only the session UUID and the text
 * varying between calls.
 */
object RtiPayloads {

    private fun classEntry(name: String): Map<String, Any?> = linkedMapOf(
        "\$classname" to name,
        "\$classes" to listOf(name, "NSObject")
    )

    private fun archive(objects: List<Any?>): ByteArray = BinaryPlist.encode(
        linkedMapOf<String, Any?>(
            "\$version" to 100000,
            "\$archiver" to "RTIKeyedArchiver",
            "\$top" to linkedMapOf<String, Any?>("textOperations" to Uid(1)),
            "\$objects" to objects
        )
    )

    /** Payload that appends [text] to the field currently focused on the TV. */
    fun insertText(sessionUuid: ByteArray, text: String): ByteArray = archive(
        listOf(
            "\$null",
            linkedMapOf<String, Any?>(
                "keyboardOutput" to Uid(2),
                "\$class" to Uid(7),
                "targetSessionUUID" to Uid(5)
            ),
            linkedMapOf<String, Any?>(
                "insertionText" to Uid(3),
                "\$class" to Uid(4)
            ),
            text,
            classEntry("TIKeyboardOutput"),
            linkedMapOf<String, Any?>(
                "NS.uuidbytes" to sessionUuid,
                "\$class" to Uid(6)
            ),
            classEntry("NSUUID"),
            classEntry("RTITextOperations")
        )
    )

    /** Payload that wipes whatever is already in the focused field. */
    fun clearText(sessionUuid: ByteArray): ByteArray = archive(
        listOf(
            "\$null",
            linkedMapOf<String, Any?>(
                "\$class" to Uid(7),
                "targetSessionUUID" to Uid(5),
                "keyboardOutput" to Uid(2),
                "textToAssert" to Uid(4)
            ),
            linkedMapOf<String, Any?>("\$class" to Uid(3)),
            classEntry("TIKeyboardOutput"),
            "",
            linkedMapOf<String, Any?>(
                "NS.uuidbytes" to sessionUuid,
                "\$class" to Uid(6)
            ),
            classEntry("NSUUID"),
            classEntry("RTITextOperations")
        )
    )
}
