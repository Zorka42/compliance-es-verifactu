package dev.verifactu.tools.query

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties
import kotlin.system.exitProcess

/** Runs the local query preparation and saved-response inspection tool. */
public fun main(args: Array<String>) {
    try {
        println(runCommand(args.toList()))
    } catch (error: QueryInputException) {
        System.err.println(error.message)
        exitProcess(2)
    } catch (_: java.nio.file.InvalidPathException) {
        System.err.println("Invalid local file path.")
        exitProcess(2)
    } catch (_: IOException) {
        System.err.println("Cannot read or create the requested local file. Existing output files are never overwritten.")
        exitProcess(2)
    }
}

internal fun runCommand(args: List<String>): String {
    if (args.isEmpty() || args == listOf("--help")) return USAGE
    val options = parseOptions(args.drop(1))
    return when (args.first()) {
        "prepare" -> prepareCommand(options)
        "inspect" -> inspectCommand(options)
        else -> throw QueryInputException(USAGE)
    }
}

private fun prepareCommand(options: Map<String, String>): String {
    options.allow("direction", "party-file", "year", "month", "out", "page-from")
    val direction =
        when (options.required("direction")) {
            "issued" -> QueryDirection.ISSUED
            "received" -> QueryDirection.RECEIVED
            else -> throw QueryInputException("Direction must be issued or received.")
        }
    val identity = readIdentity(options.required("party-file"))
    val party =
        QueryParty(
            identity.getProperty("nif"),
            identity.getProperty("name"),
        )
    val query = InvoiceQuery()
    val previous = options["page-from"]?.let { query.inspect(readLimited(it)) }
    val request = query.prepare(direction, party, QueryPeriod(options.required("year"), options.required("month")), previous)
    Files.writeString(Path.of(options.required("out")), request, Charsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    return "Prepared schema-validated SOAP query locally. No request was sent."
}

private fun readIdentity(path: String): Properties {
    val identity = Properties()
    try {
        identity.load(readLimited(path).reader())
    } catch (_: IllegalArgumentException) {
        throw QueryInputException("Party file must contain valid UTF-8 properties.")
    }
    queryRequire(
        identity.getProperty("nif") != null && identity.getProperty("name") != null,
        "Party file requires nif and name properties.",
    )
    return identity
}

private fun inspectCommand(options: Map<String, String>): String {
    options.allow("input", "details")
    val page = InvoiceQuery().inspect(readLimited(options.required("input")))
    val counts =
        page.records
            .groupingBy { it.status }
            .eachCount()
            .toSortedMap()
    val summary =
        "Direction: ${page.direction.name.lowercase()}; period: ${page.period.year}-${page.period.month}\n" +
            "VERI*FACTU records on this page: ${page.records.size}; more pages: ${page.next != null}\n" +
            "States: $counts\n" +
            "Coverage is limited to records submitted through VERI*FACTU. No network access occurred."
    if ("details" !in options) return summary
    return summary + "\nissuer\tinvoice\tissue_date\tstate\ttotal\n" +
        page.records.joinToString("\n") {
            listOf(it.invoice.issuer, it.invoice.number, it.invoice.date, it.status, it.total.orEmpty()).joinToString("\t") { field ->
                field
                    .flatMap { character ->
                        if (character.isISOControl() || character in '\u202A'..'\u202E' || character in '\u2066'..'\u2069') {
                            "\\u${character.code.toString(16).padStart(4, '0')}".toList()
                        } else {
                            listOf(character)
                        }
                    }.joinToString("")
            }
        }
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val options = mutableMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index].removePrefix("--")
        if (!args[index].startsWith("--") ||
            key.isBlank() ||
            key in options
        ) {
            throw QueryInputException("Invalid or repeated command option.")
        }
        index++
        val value =
            if (key ==
                "details"
            ) {
                "true"
            } else {
                args.getOrNull(index++) ?: throw QueryInputException("A command option needs a value.")
            }
        options[key] = value
    }
    return options
}

private fun Map<String, String>.allow(vararg names: String) {
    if (keys.any { it !in names }) throw QueryInputException("Unsupported command option.")
}

private fun Map<String, String>.required(name: String): String = get(name) ?: throw QueryInputException("Missing --$name option.")

private fun readLimited(path: String): String =
    Files.newInputStream(Path.of(path)).use {
        val bytes = it.readNBytes(MAX_XML_BYTES + 1)
        if (bytes.size > MAX_XML_BYTES) throw QueryInputException("Input exceeds the tool's 64 MiB limit.")
        try {
            Charsets.UTF_8
                .newDecoder()
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw QueryInputException("Input must be valid UTF-8.")
        }
    }

private const val USAGE =
    "Local VERI*FACTU query tool (no network or certificate access).\n" +
        "prepare --direction issued|received --party-file party.properties --year YYYY --month MM " +
        "--out request.xml [--page-from response.xml]\n" +
        "inspect --input response.xml [--details]\n" +
        "party.properties is UTF-8 with nif and name keys. Default inspection omits personal invoice details."
