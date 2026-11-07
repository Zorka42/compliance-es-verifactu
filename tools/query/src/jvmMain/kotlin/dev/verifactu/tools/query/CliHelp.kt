package dev.verifactu.tools.query

internal val LEAF_COMMANDS: Set<List<String>> =
    setOf(
        listOf("config", "init"),
        listOf("sandbox", "demo"),
        listOf("sandbox", "invoices", "list"),
        listOf("sandbox", "scenarios"),
        listOf("sandbox", "prepare"),
        listOf("sandbox", "show"),
        listOf("sandbox", "inspect"),
        listOf("sandbox", "send"),
    ) + listOf("init", "prepare", "show", "send", "query", "report", "rehearse").map { listOf("sandbox", "matrix", it) }

internal val MATRIX_ACTIONS: List<String> = listOf("init", "prepare", "show", "send", "query", "report", "rehearse")

internal val DEMO_RESPONSES: List<String> =
    listOf(
        "accepted",
        "warning",
        "rejected",
        "duplicate",
        "flow-control",
        "soap-fault",
        "timeout",
        "not-sent",
    )

internal fun commandHelp(command: List<String>): String =
    if (command.take(2) == listOf("sandbox", "matrix")) matrixHelp(command.drop(2)) else baseCommandHelp(command)

private fun baseCommandHelp(command: List<String>): String =
    when (command) {
        emptyList<String>() ->
            """
            Work with VERI*FACTU from your terminal.

            USAGE
              verifactu <command> <subcommand> [flags]

            COMMANDS
              config init             Configure the taxpayer locally
              sandbox matrix          Prepare and verify the ZA-111 TEST matrix
              sandbox scenarios       List available preparation/response scenarios
              sandbox prepare         Prepare a local request bundle
              sandbox show            Review XML, TEST endpoint and request digest
              sandbox demo            Run synthetic scenarios offline
              sandbox inspect         Inspect a saved response locally
              sandbox send            Send one reviewed TEST bundle (macOS Keychain)
              sandbox invoices list   Read issued and received VERI*FACTU TEST records (macOS)

            EXAMPLES
              verifactu sandbox demo
              verifactu sandbox demo --response timeout
              verifactu config init
              verifactu sandbox invoices list --month 2026-11

            HELP
              verifactu <command> --help
              verifactu help sandbox invoices list

            No command runs implicitly. Only explicit TEST consultation or send commands access Keychain and AEAT.
            """.trimIndent()
        listOf("config") -> "USAGE\n  verifactu config <command>\n\nCOMMANDS\n  init   Configure the taxpayer locally"
        listOf("config", "init") ->
            """
            Configure the taxpayer's NIF and legal name. Prompts in an interactive terminal.
            Stores only these fields in ~/.config/verifactu/taxpayer.properties (0600).
            Never overwrites existing configuration. No certificate, password or network access.

            USAGE
              verifactu config init [--party-file FILE]

            FLAGS
              --party-file FILE   Read nif and name from UTF-8 properties instead of prompting
              -h, --help          Show this help

            NEXT
              verifactu sandbox invoices list
            """.trimIndent()
        listOf(
            "sandbox",
        ),
        ->
            "USAGE\n  verifactu sandbox <command>\n\nCOMMANDS\n" +
                "  matrix     Prepare and verify the ZA-111 TEST matrix\n" +
                "  scenarios  List scenarios\n  prepare    Prepare a local bundle\n  show       Review XML and digest\n" +
                "  demo       Run offline scenarios\n  inspect    Inspect saved response XML\n" +
                "  send       Send a reviewed TEST bundle (macOS Keychain)\n  invoices   Consult TEST records"
        listOf(
            "sandbox",
            "invoices",
        ),
        ->
            "USAGE\n  verifactu sandbox invoices <command>\n\nCOMMANDS\n" +
                "  list   Read issued and received TEST records (macOS Keychain)"
        listOf("sandbox", "demo") ->
            """
            Run synthetic registration response scenarios. No setup, Keychain or network access.
            Without --bundle, creates and removes temporary synthetic inputs automatically.

            USAGE
              verifactu sandbox demo [--bundle DIRECTORY] [--response NAME]

            FLAGS
              --bundle DIR      Use an existing bundle; still no network or Keychain access
              --response NAME   Run one response instead of all eight:
                                accepted, warning, rejected, duplicate, flow-control,
                                soap-fault, timeout, not-sent
              -h, --help        Show this help

            EXAMPLES
              verifactu sandbox demo
              verifactu sandbox demo --response timeout
            """.trimIndent()
        listOf("sandbox", "invoices", "list") ->
            """
            Read VERI*FACTU TEST records using the macOS native certificate picker.
            Defaults to both directions and the current month in Europe/Madrid.
            This consultation cannot register, correct or cancel invoices.
            The list covers only records submitted through VERI*FACTU in TEST.

            USAGE
              verifactu sandbox invoices list [flags]

            FLAGS
              --month YYYY-MM      Month to consult (default: current Madrid month)
              --direction VALUE    issued, received, or both (default: both)
              --format VALUE       table or tsv (default: table)
              --issuer NAME        Show identities with this exact issuer common name
              --party-file FILE    Override the locally configured taxpayer
              --out NEW_DIRECTORY  Keep XML/list evidence here (default: private temporary directory)
              -h, --help           Show this help without certificate or network access

            EXAMPLES
              verifactu config init
              verifactu sandbox invoices list --month 2026-11
              verifactu sandbox invoices list --direction received --format tsv

            OUTPUT
              Invoice rows go to stdout; diagnostics and the evidence path go to stderr.
              Evidence is retained even on failure. Partial lists return a nonzero status.
              Exit: 0 complete; 2 local input; 3 response needs review; 4 transport/artifact failure.
              No automatic retries. macOS controls whether key use requires password or Touch ID.
            """.trimIndent()
        else -> sandboxActionHelp(command)
    }
