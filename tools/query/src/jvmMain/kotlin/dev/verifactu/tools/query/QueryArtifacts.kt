package dev.verifactu.tools.query

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Properties

internal fun privateDirectory(path: Path) {
    Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
}

internal fun privateFile(
    path: Path,
    text: String,
) {
    Files
        .newByteChannel(
            path,
            setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        ).use { channel ->
            val data = java.nio.ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
            while (data.hasRemaining()) channel.write(data)
        }
}

internal fun readPrivateInput(
    path: Path,
    limit: Int,
): String {
    queryRequire(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Select a regular local input file, not a link.")
    val data = Files.newInputStream(path).use { it.readNBytes(limit + 1) }
    queryRequire(data.size <= limit, "Input exceeds the selected size limit.")
    return try {
        Charsets.UTF_8
            .newDecoder()
            .decode(java.nio.ByteBuffer.wrap(data))
            .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw QueryInputException("Input must be valid UTF-8.")
    }
}

internal fun queryProperties(path: Path): Map<String, String> {
    val values =
        object : Properties() {
            override fun put(
                key: Any,
                value: Any,
            ): Any? {
                queryRequire(!containsKey(key), "Repeated property key.")
                return super.put(key, value)
            }
        }
    try {
        values.load(readPrivateInput(path, 16_384).reader())
    } catch (_: IllegalArgumentException) {
        throw QueryInputException("Invalid or repeated UTF-8 properties.")
    }
    return values.stringPropertyNames().associateWith { values.getProperty(it) }
}

internal fun queryDigest(xml: String): String =
    MessageDigest.getInstance("SHA-256").digest(xml.toByteArray(Charsets.UTF_8)).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }

internal fun queryField(text: String): String =
    buildString {
        text.forEach { character ->
            when {
                character == '\\' -> append("\\\\")
                character.isISOControl() || character in '\u202A'..'\u202E' || character in '\u2066'..'\u2069' ->
                    append("\\u${character.code.toString(16).padStart(4, '0')}")
                else -> append(character)
            }
        }
    }
