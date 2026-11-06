package dev.verifactu.tools.query

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliTest {
    @Test
    fun everyCommandAndGroupHasHelpWithoutSideEffects() =
        workspace { root ->
            val commands = listOf(emptyList(), listOf("config"), listOf("sandbox"), listOf("sandbox", "invoices")) + LEAF_COMMANDS
            commands.forEach { words ->
                listOf(words + "--help", words + "-h", listOf("help") + words).forEach { args ->
                    val result = cliResult(args, settings(root), { error("No network") }, { error("No demo") })
                    assertEquals(0, result.exitCode, args.toString())
                    assertTrue(result.output.contains("USAGE"))
                    assertTrue(result.diagnostic.isEmpty())
                }
            }
            assertTrue(cliResult(emptyList(), settings(root)).output.contains("sandbox invoices list"))
            assertFalse(Files.exists(root.resolve("config")))
        }

    @Test
    fun mistakesFailBeforeAnyOperationalActionWithActionableHelp() =
        workspace { root ->
            val commands =
                listOf(
                    listOf("sandbxo"),
                    listOf("sandbox", "send"),
                    listOf("sandbox", "--other", "x"),
                    listOf("sandbox", "demo", "--response", "unknown"),
                    listOf("sandbox", "demo", "--response"),
                    listOf("sandbox", "demo", "--response=timeout", "--response=accepted"),
                    listOf("sandbox", "demo", "--response="),
                    listOf("sandbox", "demo", "--response", "--help"),
                    listOf("sandbox", "demo", "--bad=x"),
                    listOf("sandbox", "demo", "--response=timeout", "unexpected"),
                )
            commands.forEach { args ->
                val result = cliResult(args, settings(root), { error("No fetch") }, { error("No demo") })
                assertEquals(2, result.exitCode, args.toString())
                assertTrue(result.diagnostic.contains("Error:"))
                assertTrue(result.output.isEmpty())
            }
        }

    @Test
    fun initializationPromptsStoresOnlyIdentityPrivatelyAndDoesNotOverwrite() =
        workspace { root ->
            val answers = ArrayDeque(listOf("89890001K", "Synthetic taxpayer"))
            val settings = settings(root).copy(prompt = { answers.removeFirst() })
            assertEquals(0, cliResult(listOf("config", "init"), settings).exitCode)
            val file = settings.taxpayerFile
            assertEquals(mapOf("nif" to "89890001K", "name" to "Synthetic taxpayer"), queryProperties(file))
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(file.parent))
            val before = Files.readString(file)
            assertEquals(2, cliResult(listOf("config", "init"), settings).exitCode)
            assertEquals(before, Files.readString(file))
            assertTrue(answers.isEmpty())
        }

    @Test
    fun initializationSupportsFileAndRejectsMissingInputInvalidIdentityAndSecrets() =
        workspace { root ->
            val settings = settings(root)
            assertTrue(cliResult(listOf("config", "init"), settings).diagnostic.contains("--party-file"))
            val party = root.resolve("input.properties")
            listOf("nif=89890001K\nname=\n", "nif=89890001K\nname=Synthetic\npassword=PRIVATE\n").forEach { text ->
                Files.writeString(party, text)
                assertEquals(2, cliResult(listOf("config", "init", "--party-file", party.toString()), settings).exitCode)
                assertFalse(Files.exists(settings.taxpayerFile))
            }
            Files.writeString(party, "nif=89890001K\nname=Synthetic taxpayer\n")
            assertEquals(0, cliResult(listOf("config", "init", "--party-file=$party"), settings).exitCode)
        }

    @Test
    fun listDefaultsToBothAndCurrentMonthAndSeparatesTableFromDiagnostics() =
        workspace { root ->
            val settings = initialized(root)
            var output: Path? = null
            val result =
                cliResult(listOf("sandbox", "invoices", "list"), settings, { options ->
                    assertEquals("both", options["direction"])
                    assertEquals("2026", options["year"])
                    assertEquals("11", options["month"])
                    assertEquals("true", options["keychain"])
                    val directory = Path.of(options.getValue("out"))
                    output = directory
                    privateDirectory(directory)
                    privateFile(directory.resolve("invoices.tsv"), ROWS)
                    QueryRunResult("COMPLETE: consulted TEST")
                })
            try {
                assertEquals(0, result.exitCode)
                assertTrue(result.output.contains("INV-001"))
                assertFalse(result.output.contains("COMPLETE"))
                assertFalse(result.output.contains('\t'))
                assertTrue(result.diagnostic.contains("COMPLETE"))
                assertTrue(result.diagnostic.contains("Evidence directory:"))
            } finally {
                output?.parent?.toFile()?.deleteRecursively()
            }
        }

    @Test
    fun listAcceptsExplicitInputsAndProducesScriptableTsvIncludingPartialFailure() =
        workspace { root ->
            val settings = initialized(root)
            val target = root.resolve("read")
            val result =
                cliResult(
                    listOf(
                        "sandbox",
                        "invoices",
                        "list",
                        "--month=2024-02",
                        "--direction=received",
                        "--format=tsv",
                        "--party-file",
                        settings.taxpayerFile.toString(),
                        "--out",
                        target.toString(),
                    ),
                    settings,
                    { options ->
                        assertEquals("2024", options["year"])
                        assertEquals("02", options["month"])
                        assertEquals("received", options["direction"])
                        privateDirectory(target)
                        privateFile(target.resolve("invoices.tsv"), ROWS)
                        QueryRunResult("INCOMPLETE: later page failed", 3)
                    },
                )
            assertEquals(3, result.exitCode)
            assertEquals(ROWS.trimEnd(), result.output)
            assertTrue(result.diagnostic.contains("INCOMPLETE"))
        }

    @Test
    fun invalidListOptionsAndMissingTaxpayerFailBeforeFetching() =
        workspace { root ->
            val base = listOf("sandbox", "invoices", "list")
            assertTrue(cliResult(base, settings(root)).diagnostic.contains("config init"))
            val settings = initialized(root)
            listOf("--month=2026-13", "--month=26-01", "--direction=other", "--format=json", "--endpoint=private").forEach { arg ->
                assertEquals(2, cliResult(base + arg, settings, { error("No fetch") }).exitCode)
            }
            val failed = cliResult(base + listOf("--out", root.resolve("missing").toString()), settings, { QueryRunResult("NOT_SENT", 4) })
            assertEquals(4, failed.exitCode)
            assertTrue(failed.output.isEmpty())
            assertTrue(failed.diagnostic.contains("NOT_SENT"))
        }

    @Test
    fun demoHandlesAllOrOneScenarioAndCleansItsAutomaticWorkspace() =
        workspace { root ->
            listOf(emptyList(), listOf("--response=timeout")).forEach { flags ->
                val calls = mutableListOf<List<String>>()
                var config: Path? = null
                val result =
                    cliResult(listOf("sandbox", "demo") + flags, settings(root), { error("No fetch") }, { args ->
                        calls += args
                        if (args.first() == "prepare") {
                            config = Path.of(args[args.indexOf("--config") + 1])
                            assertTrue(Files.readString(config!!).contains("Synthetic sandbox issuer"))
                        }
                    })
                assertEquals(0, result.exitCode)
                assertEquals(listOf("prepare", "demo"), calls.map { it.first() })
                assertEquals(flags.isNotEmpty(), "--response" in calls.last())
                assertFalse(Files.exists(config!!.parent))
            }
        }

    @Test
    fun localErrorsAreSanitizedAndMalformedSavedRowsNeverAppearAsValidTables() =
        workspace { root ->
            val settings = initialized(root)
            listOf(
                java.io.IOException("PRIVATE"),
                SecurityException("PRIVATE"),
                java.nio.file.InvalidPathException("PRIVATE", "PRIVATE"),
            ).forEach { error ->
                val result =
                    cliResult(listOf("sandbox", "invoices", "list", "--out", root.resolve("unused").toString()), settings, { throw error })
                assertEquals(2, result.exitCode)
                assertFalse(result.diagnostic.contains("PRIVATE"))
            }
            assertEquals("", invoiceTable(""))
            kotlin.test.assertFailsWith<QueryInputException> { invoiceTable("one\ttwo") }
        }

    @Test
    fun brokenSavedEvidenceAfterFetchReturnsFailureAndKeepsTheEvidencePath() =
        workspace { root ->
            val settings = initialized(root)
            val directory = root.resolve("broken")
            val result =
                cliResult(
                    listOf("sandbox", "invoices", "list", "--out", directory.toString()),
                    settings,
                    {
                        privateDirectory(directory)
                        privateFile(directory.resolve("invoices.tsv"), "invalid\trow")
                        QueryRunResult("COMPLETE")
                    },
                )
            assertEquals(4, result.exitCode)
            assertTrue(result.output.isEmpty())
            assertTrue(result.diagnostic.contains(directory.toString()))
            assertTrue(Files.exists(directory.resolve("invoices.tsv")))
        }

    @Test
    fun realEntryPointsDisplayHelpAndRejectMissingInstalledNativeHelperWithoutCertificateAccess() =
        workspace { root ->
            val output = java.io.ByteArrayOutputStream()
            val previous = System.out
            try {
                System.setOut(java.io.PrintStream(output, true, "UTF-8"))
                listOf("CliKt", "MainKt").forEach { name ->
                    Class
                        .forName("dev.verifactu.tools.query.$name")
                        .getMethod("main", Array<String>::class.java)
                        .invoke(null, arrayOf("--help"))
                }
            } finally {
                System.setOut(previous)
            }
            assertTrue(output.toString("UTF-8").contains("USAGE"))
            val settings = initialized(root)
            val result = cliResult(listOf("sandbox", "invoices", "list", "--out", root.resolve("native").toString()), settings)
            assertEquals(2, result.exitCode)
            assertFalse(Files.exists(root.resolve("native")))
        }

    private fun initialized(root: Path): CliSettings {
        val settings = settings(root).copy(prompt = { if (it.contains("NIF")) "89890001K" else "Synthetic taxpayer" })
        assertEquals(0, cliResult(listOf("config", "init"), settings).exitCode)
        return settings
    }

    private fun settings(root: Path): CliSettings = CliSettings(root.resolve("config/taxpayer.properties"), "2026-11") { null }

    private fun workspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("verifactu-cli-test-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ROWS = "direction\tissuer\tinvoice\tissue_date\tstate\ttotal\nissued\t89890001K\tINV-001\t01-11-2026\tCorrecto\t121.00\n"
    }
}
