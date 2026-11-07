package dev.verifactu.tools.sandbox

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Properties

internal fun readText(
    path: Path,
    limit: Int = 8 * 1024 * 1024,
): String {
    sandboxRequire(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Select a regular local input file, not a link.")
    val bytes = Files.newInputStream(path).use { it.readNBytes(limit + 1) }
    sandboxRequire(bytes.size <= limit, "The input file exceeds the tool's size limit.")
    return try {
        Charsets.UTF_8
            .newDecoder()
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw SandboxInputException("Input files must be valid UTF-8.")
    }
}

internal fun readProperties(path: Path): Map<String, String> {
    val properties =
        object : Properties() {
            override fun put(
                key: Any,
                value: Any,
            ): Any? {
                sandboxRequire(!containsKey(key), "Properties must not contain repeated keys.")
                return super.put(key, value)
            }
        }
    try {
        properties.load(readText(path, 16_384).reader())
    } catch (_: IllegalArgumentException) {
        throw SandboxInputException("Invalid or repeated UTF-8 properties.")
    }
    return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
}

internal fun propertiesText(values: Map<String, String>): String =
    values.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) ->
        key + "=" +
            buildString {
                value.forEach { char ->
                    append(
                        when (char) {
                            '\n' -> "\\n"
                            '\r' -> "\\r"
                            '\t' -> "\\t"
                            '\\', ' ', '=', ':', '#', '!' -> "\\$char"
                            else -> char.toString()
                        },
                    )
                }
            }
    }

internal fun createPrivateDirectory(path: Path) {
    if (Files.getFileStore(path.toAbsolutePath().parent).supportsFileAttributeView("posix")) {
        Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    } else {
        Files.createDirectory(path)
    }
}

internal fun writePrivate(
    path: Path,
    text: String,
) {
    val options = setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    val channel =
        if (Files.getFileStore(path.toAbsolutePath().parent).supportsFileAttributeView("posix")) {
            Files.newByteChannel(path, options, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            Files.newByteChannel(path, options)
        }
    channel.use {
        val bytes = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
        while (bytes.hasRemaining()) channel.write(bytes)
    }
}

internal fun requestDigest(xml: String): String =
    MessageDigest.getInstance("SHA-256").digest(xml.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
        (byte.toInt() and 255).toString(16).padStart(2, '0')
    }

internal fun terminalText(value: String): String =
    buildString {
        value.take(1024).forEach { char ->
            if (char.isISOControl() || char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069') {
                append("\\u${char.code.toString(16).padStart(4, '0')}")
            } else {
                append(char)
            }
        }
        if (value.length > 1024) append("… [see saved XML]")
    }
