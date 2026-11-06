package dev.verifactu.tools.sandbox

import dev.verifactu.core.ChainState
import dev.verifactu.xml.SubmissionRecord
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SandboxPreparationTest {
    @Test
    fun preparesDeterministicSchemaValidatedRegistrationAndRedactsDefaultOutput() =
        withWorkspace { root ->
            val first = fixtureBundle(root)
            val second =
                prepareBundle(
                    BundlePreparation(
                        root.resolve("bundle-inputs.properties"),
                        root.resolve("second"),
                        "registration",
                        "offline",
                        "standard",
                        null,
                    ),
                )
            assertEquals(first.sha256, second.sha256)
            assertEquals(first.plan.soap, second.plan.soap)
            assertEquals(first, loadBundle(root.resolve("bundle")).let { it.copy(plan = first.plan) })
            assertTrue(first.plan.qr!!.contains("prewww2.aeat.es"))
            assertTrue(first.plan.report.isValid)
            val summary = bundleSummary(first)
            assertFalse(summary.contains("89890001K"))
            assertFalse(first.toString().contains("89890001K"))
            assertFalse(first.plan.toString().contains("89890001K"))
            val preview = runCommand(listOf("show", "--bundle", root.resolve("bundle").toString())).output
            assertTrue(preview.contains(first.sha256))
            assertTrue(preview.contains(first.plan.soap))
            if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root.resolve("bundle")))
                assertEquals(
                    PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(root.resolve("bundle/request.xml")),
                )
            }
        }

    @Test
    fun appendsToAnExplicitChainAndCancelsTheMatchingRegistration() =
        withWorkspace { root ->
            val first = fixtureBundle(root)
            val nextConfig =
                configFile(
                    root,
                    TEST_INPUTS + mapOf("invoice.number" to "SANDBOX-002", "generated.at" to "2024-01-01T12:01:00+01:00"),
                    "next.properties",
                )
            val second =
                prepareBundle(
                    BundlePreparation(nextConfig, root.resolve("second"), "registration", "offline", "standard", root.resolve("bundle")),
                )
            assertEquals(first.plan.next, assertIs<SubmissionRecord.Registration>(second.plan.record).value.draft.chainState)
            val cancelConfig =
                configFile(
                    root,
                    TEST_INPUTS + mapOf("invoice.number" to "SANDBOX-002", "generated.at" to "2024-01-01T12:02:00+01:00"),
                    "cancel.properties",
                )
            val cancelled =
                prepareBundle(
                    BundlePreparation(cancelConfig, root.resolve("cancel"), "cancellation", "offline", "standard", root.resolve("second")),
                )
            assertEquals(second.plan.next, assertIs<SubmissionRecord.Cancellation>(cancelled.plan.record).value.draft.chainState)
            assertEquals(cancelled.sha256, loadBundle(root.resolve("cancel")).sha256)
            assertEquals(null, cancelled.plan.qr)
            assertFalse(Files.exists(root.resolve("cancel/qr.txt")))
            assertIs<ChainState.PreviousRecord>(cancelled.plan.next)
        }

    @Test
    fun duplicateRetainsEveryRequestByteWithoutCreatingANewFiscalRecord() =
        withWorkspace { root ->
            val first = fixtureBundle(root)
            val duplicate =
                prepareBundle(
                    BundlePreparation(null, root.resolve("duplicate"), "duplicate", "offline", "standard", root.resolve("bundle")),
                )
            assertEquals(first.plan.soap, duplicate.plan.soap)
            assertEquals(first.plan.next, duplicate.plan.next)
            assertEquals(first.sha256, duplicate.sha256)
            assertEquals("duplicate", loadBundle(root.resolve("duplicate")).values["scenario"])
        }

    @Test
    fun rejectsUnboundCancellationDifferentChainsAndImplicitModeChanges() =
        withWorkspace { root ->
            fixtureBundle(root)
            val config = configFile(root, TEST_INPUTS, "other.properties")
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(config, root.resolve("a"), "cancellation", "offline", "standard", null))
            }
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(config, root.resolve("b"), "registration", "aeat-test", "standard", root.resolve("bundle")))
            }
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(config, root.resolve("c"), "registration", "offline", "seal", root.resolve("bundle")))
            }
            val changed = configFile(root, TEST_INPUTS + ("issuer.name" to "Other issuer"), "changed.properties")
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(changed, root.resolve("d"), "registration", "offline", "standard", root.resolve("bundle")))
            }
            val otherNumber = configFile(root, TEST_INPUTS + ("invoice.number" to "OTHER-1"), "wrong.properties")
            assertFailsWith<SandboxInputException> {
                prepareBundle(
                    BundlePreparation(otherNumber, root.resolve("e"), "cancellation", "offline", "standard", root.resolve("bundle")),
                )
            }
            prepareBundle(BundlePreparation(config, root.resolve("cancel"), "cancellation", "offline", "standard", root.resolve("bundle")))
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(null, root.resolve("f"), "duplicate", "offline", "standard", root.resolve("cancel")))
            }
        }

    @Test
    fun rejectsModifiedRequestsMetadataAndRecordsInsteadOfSendingDifferentBytes() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val request = root.resolve("bundle/request.xml")
            Files.writeString(request, bundle.plan.soap + " ")
            assertFailsWith<SandboxInputException> { loadBundle(root.resolve("bundle")) }
            Files.writeString(request, bundle.plan.soap)
            val metadata = root.resolve("bundle/bundle.properties")
            Files.writeString(metadata, propertiesText(bundle.values + ("issuer.name" to "Tampered")))
            assertFailsWith<SandboxInputException> { loadBundle(root.resolve("bundle")) }
            Files.writeString(metadata, propertiesText(bundle.values + ("format" to "999")))
            assertFailsWith<SandboxInputException> { loadBundle(root.resolve("bundle")) }
            Files.writeString(metadata, propertiesText(bundle.values))
            Files.writeString(root.resolve("bundle/record.xml"), "changed")
            assertFailsWith<SandboxInputException> { loadBundle(root.resolve("bundle")) }
        }

    @Test
    fun rejectsBadConfigurationAndInvalidFiscalValuesBeforeCreatingOutput() =
        withWorkspace { root ->
            val invalid =
                listOf(
                    TEST_INPUTS - "issuer.name",
                    TEST_INPUTS + ("endpoint" to "https://example.invalid/"),
                    TEST_INPUTS + ("invoice.date" to "bad-date"),
                    TEST_INPUTS + ("issuer.nif" to "12345678A"),
                )
            invalid.forEachIndexed { index, fields ->
                val config = configFile(root, fields, "$index.properties")
                assertFailsWith<SandboxInputException> {
                    prepareBundle(
                        BundlePreparation(
                            config,
                            root.resolve("out-$index"),
                            "registration",
                            "offline",
                            "standard",
                            null,
                        ),
                    )
                }
                assertFalse(Files.exists(root.resolve("out-$index")))
            }
            val valid = configFile(root)
            listOf("production", "TEST").forEach { mode ->
                assertFailsWith<SandboxInputException> {
                    prepareBundle(BundlePreparation(valid, root.resolve(mode), "registration", mode, "standard", null))
                }
            }
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(valid, root.resolve("route"), "registration", "offline", "other", null))
            }
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(valid, root.resolve("type"), "other", "offline", "standard", null))
            }
            assertFailsWith<SandboxInputException> {
                prepareBundle(
                    BundlePreparation(
                        null,
                        root.resolve("missing"),
                        "registration",
                        "offline",
                        "standard",
                        null,
                    ),
                )
            }
            assertFailsWith<SandboxInputException> {
                prepareBundle(
                    BundlePreparation(
                        null,
                        root.resolve("duplicate"),
                        "duplicate",
                        "offline",
                        "standard",
                        null,
                    ),
                )
            }
        }

    @Test
    fun rejectsRepeatedKeysUnsafeFilesOversizeAndMalformedUtf8() =
        withWorkspace { root ->
            val file = root.resolve("data")
            Files.writeString(file, "name=one\nna\\me=two\n")
            assertFailsWith<SandboxInputException> { readProperties(file) }
            Files.writeString(file, "name=\\uZZZZ")
            assertFailsWith<SandboxInputException> { readProperties(file) }
            Files.write(file, byteArrayOf(0xC3.toByte(), 0x28))
            assertFailsWith<SandboxInputException> { readText(file) }
            Files.writeString(file, "x".repeat(17_000))
            assertFailsWith<SandboxInputException> { readProperties(file) }
            assertFailsWith<SandboxInputException> { readText(root) }
            Files.createSymbolicLink(root.resolve("link"), file)
            assertFailsWith<SandboxInputException> { readText(root.resolve("link")) }
            assertEquals(
                " leading\nvalue=:#\\\t",
                readProperties(configFile(root, mapOf("k" to " leading\nvalue=:#\\\t"), "roundtrip")).getValue("k"),
            )
        }

    @Test
    fun neverOverwritesExistingBundlesOrFiles() =
        withWorkspace { root ->
            val original = fixtureBundle(root)
            assertFailsWith<java.nio.file.FileAlreadyExistsException> { fixtureBundle(root) }
            assertEquals(original.sha256, loadBundle(root.resolve("bundle")).sha256)
            val file = root.resolve("keep")
            writePrivate(file, "original")
            assertFailsWith<java.nio.file.FileAlreadyExistsException> { writePrivate(file, "replacement") }
            assertEquals("original", Files.readString(file))
        }
}
