package dev.verifactu.tools.sandbox

import java.nio.file.Path

internal fun parseOptions(args: List<String>): Map<String, String> {
    val options = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val token = args[index++]
        sandboxRequire(token.startsWith("--") && token.length > 2, "Expected a named command option.")
        val key = token.removePrefix("--")
        sandboxRequire(key !in options, "Repeated command option.")
        val value =
            if (key == "details") {
                "true"
            } else {
                val next = args.getOrNull(index++) ?: throw SandboxInputException("A command option needs a value.")
                sandboxRequire(!next.startsWith("--"), "A command option needs a value.")
                next
            }
        options[key] = value
    }
    return options
}

internal fun Map<String, String>.allow(vararg names: String) {
    sandboxRequire(keys.all { it in names }, "Unsupported command option.")
}

internal fun Map<String, String>.path(name: String): Path = Path.of(required(this, name))
