package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatrixLifecycleTest {
    @Test fun allCasesPassThroughPublicBridgeAndLocalReportsRemainInspectable() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val started = matrixNow().minusMinutes(5)
            for ((index, case) in MATRIX_CASES.withIndex()) {
                prepareMatrixCase(session, case, started.plusSeconds(index.toLong()))
                if (case.id != "pagination-11") {
                    assertEquals(0, matrixTestSend(session, case, started.plusSeconds(index.toLong())).exitCode)
                } else {
                    val bundle = loadBundle(session.bundle(case))
                    val result =
                        SandboxWorkflow.sendMatrix(
                            session.root,
                            case.id,
                            bundle.sha256,
                            case.records,
                            matrixTestFactory(bundle, case),
                        )
                    assertEquals(0, result.exitCode, result.output)
                }
            }
            assertEquals(0, matrixReport(session).exitCode)
            assertTrue(matrixOverview(session).output.contains("All write cases"))
            assertEquals(
                0,
                commandLineResult(listOf("matrix", "show", "--run", session.root.toString(), "--case", "registration")).exitCode,
            )
            assertEquals(0, commandLineResult(listOf("matrix")).exitCode)
            assertFalse(session.toString().contains("89890001K"))
            val second =
                initializeMatrix(
                    mapOf(
                        "party-file" to root.resolve("party.properties").toString(),
                        "producer-file" to root.resolve("producer.properties").toString(),
                        "out" to root.resolve("second").toString(),
                    ),
                    MATRIX_TIME,
                )
            assertEquals(0, second.exitCode)
            val third =
                commandLineResult(
                    listOf(
                        "matrix",
                        "init",
                        "--party-file",
                        root.resolve("party.properties").toString(),
                        "--producer-file",
                        root.resolve("producer.properties").toString(),
                        "--out",
                        root.resolve("third").toString(),
                    ),
                )
            assertEquals(0, third.exitCode)
            val rejectedFile = matrixAttempts(session, session.case("rejection")).single().resolve("response.xml")
            val rejectedXml = Files.readString(rejectedFile)
            for ((old, replacement) in listOf(
                ">1152<" to ">1151<",
                "<EstadoEnvio>Incorrecto<" to "<EstadoEnvio>Correcto<",
                "<EstadoRegistro>Incorrecto<" to "<EstadoRegistro>Correcto<",
            )) {
                try {
                    Files.writeString(rejectedFile, rejectedXml.replace(old, replacement))
                    assertEquals(MatrixState.REVIEW, observeMatrixCase(session, session.case("rejection")).state)
                } finally {
                    Files.writeString(rejectedFile, rejectedXml)
                }
            }
            val original = session.case("registration")
            val attempt = matrixAttempts(session, original).single()
            val outcome = attempt.resolve("outcome.properties")
            val text = Files.readString(outcome)
            Files.writeString(outcome, text.replace("delivery=RESPONSE_RECEIVED", "delivery=NOT_SENT"))
            assertEquals(MatrixState.REVIEW, observeMatrixCase(session, original).state)
            Files.writeString(outcome, text)
        }

    @Test fun privateSessionAndBatchInputBoundariesFailBeforePreparingOrSending() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val mutants =
                listOf(
                    Triple("matrix.properties", "format=1", "format=2"),
                    Triple("matrix.properties", "period=2026-11", "period=2026-10"),
                    Triple("matrix.properties", "invoice.prefix=VF111-", "invoice.prefix=bad"),
                    Triple("base.properties", "issuer.nif=89890001K", "other.nif=89890001K"),
                    Triple("base.properties", "issuer.nif=89890001K", "issuer.nif=89890001K\nunsupported=true"),
                    Triple("base.properties", "recipient.nif=89890001K", "recipient.nif=89890002Q"),
                    Triple("base.properties", "recipient.name=", "recipient.name=Changed"),
                    Triple("party.properties", "nif=89890001K", "nif=89890002Q"),
                )
            for ((name, old, replacement) in mutants) {
                val path = session.root.resolve(name)
                val saved = Files.readString(path)
                try {
                    Files.writeString(path, saved.replace(old, replacement))
                    assertFailsWith<SandboxInputException> { loadMatrix(session.root) }
                } finally {
                    Files.writeString(path, saved)
                }
            }
            val base =
                session.inputs +
                    mapOf(
                        "scenario" to "pagination",
                        "mode" to "offline",
                        "certificate.route" to "standard",
                        "batch.count" to "1",
                        "batch.start" to "1",
                    )
            for (override in listOf(
                mapOf("batch.count" to "bad"),
                mapOf("batch.count" to "0"),
                mapOf("batch.count" to "1001"),
                mapOf(
                    "batch.start" to "bad",
                ),
                mapOf("batch.start" to "0"),
                mapOf("batch.start" to "10002"),
                mapOf(
                    "batch.start" to "10001",
                    "batch.count" to "2",
                ),
                mapOf("system.name" to "x".repeat(100)),
            )) {
                assertFailsWith<SandboxInputException> { buildPlan(base + override) }
            }
            for (scenario in listOf("warning-first", "subsanation", "rejection-date", "registration")) {
                assertFailsWith<SandboxInputException> {
                    buildPlan(
                        (
                            if (scenario ==
                                "registration"
                            ) {
                                base
                            } else {
                                base - setOf("batch.count", "batch.start")
                            }
                        ) + mapOf("scenario" to scenario),
                    )
                }
            }
            val options =
                mapOf(
                    "party-file" to root.resolve("party.properties").toString(),
                    "producer-file" to root.resolve("producer.properties").toString(),
                    "out" to root.resolve("invalid").toString(),
                )
            assertFailsWith<SandboxInputException> { initializeMatrix(options, MATRIX_TIME, "bad") }
            Files.writeString(root.resolve("producer.properties"), "nif=89890001K\n")
            assertFailsWith<SandboxInputException> { initializeMatrix(options, MATRIX_TIME) }
            Files.writeString(root.resolve("party.properties"), "nif=89890001K\n")
            assertFailsWith<SandboxInputException> { initializeMatrix(options, MATRIX_TIME) }
        }

    @Test fun expectedNegativeCodesAndSafeDiagnosticsAreRequired() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val first = session.case("registration")
            prepareMatrixCase(session, first, MATRIX_TIME)
            matrixTestSend(session, first, MATRIX_TIME)
            val cases = listOf("duplicate", "warning")
            for (id in cases) {
                val case = session.case(id)
                prepareMatrixCase(session, case, MATRIX_TIME)
                matrixTestSend(session, case, MATRIX_TIME) { simulatedResponse(it, "accepted") }
                assertEquals(MatrixState.REVIEW, observeMatrixCase(session, case).state)
                val attempt = matrixAttempts(session, case).single()
                val bundle = loadBundle(session.bundle(case))
                val xml = simulatedResponse(bundle, if (id == "warning") "warning" else "duplicate") as AeatTransportResult.XmlResponse
                Files.writeString(attempt.resolve("response.xml"), xml.xml)
                assertEquals(MatrixState.PASS, observeMatrixCase(session, case).state)
            }
            val original = matrixAttempts(session, first).single().resolve("response.xml")
            val response = Files.readString(original)
            val descriptionOnly =
                response.replace(
                    "<EstadoRegistro>Correcto</EstadoRegistro>",
                    "<EstadoRegistro>Correcto</EstadoRegistro><DescripcionErrorRegistro>PRIVATE-DESCRIPTION</DescripcionErrorRegistro>",
                )
            Files.writeString(original, descriptionOnly)
            assertEquals(MatrixState.REVIEW, observeMatrixCase(session, first).state)
            assertFalse(matrixReport(session).output.contains("PRIVATE-DESCRIPTION"))
            val changed =
                response.replace(
                    "<EstadoRegistro>Correcto</EstadoRegistro>",
                    "<EstadoRegistro>Correcto</EstadoRegistro><CodigoErrorRegistro>PRIVATE-TEXT</CodigoErrorRegistro>" +
                        "<DescripcionErrorRegistro>Private description</DescripcionErrorRegistro>",
                )
            Files.writeString(original, changed)
            assertEquals(MatrixState.REVIEW, observeMatrixCase(session, first).state)
            assertFalse(matrixReport(session).output.contains("PRIVATE-TEXT"))
            assertTrue(matrixReport(session).output.contains("UNKNOWN"))
        }

    @Test fun repeatedExplicitNotSentAttemptsHaveAFiniteLimitAndUnreadableEvidenceIsUncertain() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val case = session.case("registration")
            prepareMatrixCase(session, case, MATRIX_TIME)
            matrixTestSend(session, case, MATRIX_TIME) { AeatTransportResult.NotSent("Synthetic cancellation") }
            val first = matrixAttempts(session, case).single()
            val started = first.resolve("started.properties")
            val permissions = Files.getPosixFilePermissions(started)
            try {
                Files.setPosixFilePermissions(started, emptySet())
                assertEquals(MatrixState.UNKNOWN, observeMatrixCase(session, case).state)
            } finally {
                Files.setPosixFilePermissions(started, permissions)
            }
            for (index in 2..99) {
                val next = session.directory(case).resolve("attempt-${index.toString().padStart(3, '0')}")
                createPrivateDirectory(next)
                Files.list(first).use { paths -> paths.forEach { Files.copy(it, next.resolve(it.fileName)) } }
            }
            assertFailsWith<SandboxInputException> { matrixTestSend(session, case, MATRIX_TIME) }
            assertEquals(99, matrixAttempts(session, case).size)
        }

    @Test fun exactBatchAndSingleRecordCountAreCheckedBeforeCredentialSetup() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val first = session.case("registration")
            prepareMatrixCase(session, first, MATRIX_TIME)
            val bundle = loadBundle(session.bundle(first))
            val transport =
                object : dev.verifactu.aeat.AeatTransportAdapter {
                    override fun execute(request: dev.verifactu.aeat.AeatTransportRequest): AeatTransportResult =
                        error("No credential/transport access")
                }
            assertEquals(
                2,
                SandboxWorkflow.send(session.bundle(first), root.resolve("count-failure"), bundle.sha256, transport, 2).exitCode,
            )
            assertFalse(Files.exists(root.resolve("count-failure")))
            val inputs = root.resolve("batch-inputs.properties")
            writePrivate(inputs, propertiesText(session.inputs + mapOf("batch.count" to "2", "batch.start" to "1")))
            val batch = root.resolve("batch")
            prepareBundle(BundlePreparation(inputs, batch, "pagination", "aeat-test", "standard", null))
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(null, root.resolve("duplicate-batch"), "duplicate", "aeat-test", "standard", batch))
            }
            val cancel = root.resolve("cancel-inputs.properties")
            writePrivate(cancel, propertiesText(session.inputs))
            assertFailsWith<SandboxInputException> {
                prepareBundle(BundlePreparation(cancel, root.resolve("cancel-batch"), "cancellation", "aeat-test", "standard", batch))
            }
            val standalone = root.resolve("bundle")
            prepareBundle(BundlePreparation(cancel, standalone, "registration", "aeat-test", "standard", null))
            val digest = loadBundle(standalone).sha256
            for (count in listOf("bad", "2", "1")) {
                var asked = 0
                assertFailsWith<SandboxInputException> {
                    runCommand(sendArguments(root, digest) + listOf("--confirm-records", count), environment = {
                        asked++
                        null
                    })
                }
                assertEquals(if (count == "1") 1 else 0, asked)
            }
        }
}
