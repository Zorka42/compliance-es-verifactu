package dev.verifactu.tools.sandbox

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxCommandTest {
    @Test
    fun commandWorkflowPreparesAndReviewsWithoutReadingCredentials() =
        withWorkspace { root ->
            val config = configFile(root)
            val bundle = root.resolve("bundle")
            val result =
                runCommand(
                    listOf(
                        "prepare",
                        "--scenario",
                        "registration",
                        "--config",
                        config.toString(),
                        "--out",
                        bundle.toString(),
                        "--certificate-route",
                        "seal",
                    ),
                    environment = { error("No credentials in preparation") },
                    transportFactory = {
                        _,
                        _,
                        _,
                        ->
                        error("No transport in preparation")
                    },
                )
            assertEquals(0, result.exitCode)
            assertTrue(result.output.contains("Mode: offline"))
            assertTrue(result.output.contains("https://prewww10.aeat.es/"))
            val duplicate =
                runCommand(
                    listOf(
                        "prepare",
                        "--scenario",
                        "duplicate",
                        "--previous",
                        bundle.toString(),
                        "--out",
                        root.resolve("duplicate").toString(),
                    ),
                )
            assertTrue(duplicate.output.contains("Scenario: duplicate"))
            assertEquals(loadBundle(bundle).sha256, loadBundle(root.resolve("duplicate")).sha256)
            assertTrue(
                runCommand(listOf("demo", "--bundle", bundle.toString(), "--response", "warning")).output.contains("REVIEW_REQUIRED"),
            )
            assertFailsWith<SandboxInputException> { runCommand(listOf("demo", "--bundle", bundle.toString(), "--response", "other")) }
        }

    @Test
    fun previewEscapesDirectionalTextWhileDigestStillCoversOriginalUtf8Bytes() =
        withWorkspace { root ->
            val config = configFile(root, TEST_INPUTS + ("issuer.name" to "Synthetic \u202Eissuer"))
            val bundle = prepareBundle(BundlePreparation(config, root.resolve("bundle"), "registration", "offline", "standard", null))
            val preview = runCommand(listOf("show", "--bundle", root.resolve("bundle").toString())).output
            assertFalse(preview.contains('\u202E'))
            assertTrue(preview.contains("\\u202e"))
            assertTrue(preview.contains(bundle.sha256))
            assertTrue(Files.readString(root.resolve("bundle/request.xml")).contains('\u202E'))
        }

    @Test
    fun commandBoundarySanitizesInputPathAndIoFailures() =
        withWorkspace { root ->
            assertEquals(0, commandLineResult(listOf("--help")).exitCode)
            assertEquals(2, commandLineResult(listOf("PRIVATE-UNKNOWN-COMMAND")).exitCode)
            assertEquals(2, commandLineResult(listOf("show", "--bundle", "PRIVATE\u0000PATH")).exitCode)
            val config = configFile(root)
            val result =
                commandLineResult(listOf("prepare", "--scenario", "registration", "--config", config.toString(), "--out", root.toString()))
            assertEquals(2, result.exitCode)
            assertFalse(result.output.contains(root.toString()))
            assertTrue(result.output.contains("never overwritten"))
        }

    @Test
    fun schemaValidationDoesNotFetchExternalResourcesOrEchoXml() {
        listOf("<PRIVATE/>", "<!DOCTYPE x SYSTEM 'https://example.invalid/never-fetch'><x/>").forEach {
            val failure = assertFailsWith<SandboxInputException> { validateBatchSchema(it) }
            assertFalse(failure.message.orEmpty().contains("PRIVATE"))
            assertFalse(failure.message.orEmpty().contains("example.invalid"))
        }
    }
}
