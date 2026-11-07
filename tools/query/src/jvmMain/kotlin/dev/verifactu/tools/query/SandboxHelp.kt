package dev.verifactu.tools.query

internal fun sandboxActionHelp(command: List<String>): String {
    queryRequire(command.size == 2 && command.first() == "sandbox", "Unknown command. Run 'verifactu --help'.")
    val usage =
        when (command.last()) {
            "scenarios" -> "scenarios"
            "prepare" ->
                "prepare --scenario NAME --config FILE --out NEW_DIRECTORY\n" +
                    "    [--previous BUNDLE] [--mode offline|aeat-test] [--certificate-route standard|seal]\n" +
                    "  verifactu sandbox prepare --scenario duplicate --previous BUNDLE --out NEW_DIRECTORY\n" +
                    "  NAME: registration, cancellation, cancellation-missing, warning-first,\n" +
                    "        rejection-date, subsanation, pagination"
            "show" -> "show --bundle DIRECTORY"
            "inspect" -> "inspect --bundle DIRECTORY --input RESPONSE_XML [--details]"
            "send" ->
                "send --bundle DIRECTORY --out NEW_ATTEMPT --confirm-sha256 REVIEWED_SHA256\n" +
                    "    [--confirm-records COUNT] [--issuer NAME]"
            else -> throw QueryInputException("Unknown command. Run 'verifactu --help'.")
        }
    val scenarioNote =
        if (command.last() == "prepare") {
            "\ncancellation-missing requires a distinct absent target and --previous with the actual chain tail.\n" +
                "It sets generation time once during preparation; show/send never refresh the saved bytes.\n"
        } else {
            ""
        }
    val behavior =
        if (command.last() == "send") {
            "Send exactly one reviewed aeat-test bundle through the native macOS certificate picker.\n" +
                "Writes registration/cancellation data to AEAT TEST. Review sandbox show first.\n" +
                "No production or URL override. Refuses offline bundles, wrong digests and existing output directories.\n" +
                "--issuer NAME filters by exact issuer common name. No private-key export or password flags.\n" +
                "Preserves request, response, network.log, summary.txt and outcome.properties.\n" +
                "Unknown delivery must be reconciled before another attempt; no automatic retry."
        } else {
            "Local operation only. No Keychain or network access.\n" +
                "Preparation defaults to unsendable offline mode. Use explicit private inputs for aeat-test.\n" +
                "show displays the exact XML and SHA-256; inspect omits fiscal identifiers unless --details is supplied."
        }
    return "$behavior$scenarioNote\n\nUSAGE\n  verifactu sandbox $usage\n\nHELP\n" +
        "  -h, --help   Show this help without certificate or network access"
}
