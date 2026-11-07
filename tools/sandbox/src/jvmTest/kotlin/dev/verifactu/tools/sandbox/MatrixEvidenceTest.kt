package dev.verifactu.tools.sandbox

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MatrixEvidenceTest {
    @Test fun acceptedPreCutoffRecordCannotPassTheRejectionCase() =
        withWorkspace { root ->
            val session = testMatrix(root)
            for ((index, case) in MATRIX_CASES.take(6).withIndex()) {
                val now = MATRIX_TIME.plusSeconds(index * 61L)
                prepareMatrixCase(session, case, now)
                if (case.id != "rejection") {
                    assertEquals(0, matrixTestSend(session, case, now).exitCode)
                    continue
                }
                val bundle = loadBundle(session.bundle(case))
                assertEquals("27-10-2024", bundle.values["invoice.date"])
                val result = matrixTestSend(session, case, now) { simulatedResponse(it, "accepted") }
                assertEquals(3, result.exitCode)
                assertTrue("Result: ACCEPTED" in result.output)
                assertTrue("Correlation: MATCHED" in result.output)
                assertTrue("Expected case verification: REVIEW" in result.output)
                assertEquals(MatrixState.REVIEW, observeMatrixCase(session, case).state)
            }
            val report = matrixReport(session)
            assertEquals(3, report.exitCode)
            assertTrue("case.cancellation.state=PASS" in report.output)
            assertTrue("case.rejection.state=REVIEW" in report.output)
            val blocked =
                assertFailsWith<SandboxInputException> {
                    sendMatrixCase(
                        mapOf("run" to session.root.toString(), "case" to "pagination-01"),
                        { _, _ -> error("No transport or identity access") },
                        { MATRIX_TIME.plusHours(1) },
                    )
                }
            assertTrue("Uncertain or unexpected evidence" in blocked.message.orEmpty())
            assertTrue(matrixAttempts(session, session.case("pagination-01")).isEmpty())
        }

    @Test fun incompleteUncorrelatedAndUnexpectedReceiptsCannotPass() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val case = session.case("registration")
            prepareMatrixCase(session, case, MATRIX_TIME)
            matrixTestSend(session, case, MATRIX_TIME)
            val attempt = matrixAttempts(session, case).single()
            val mutations =
                listOf(
                    Triple("started.properties", "request.sha256=", "request.sha256=wrong"),
                    Triple("outcome.properties", "request.sha256=", "request.sha256=wrong"),
                    Triple("request.xml", "RegFactuSistemaFacturacion", "Changed"),
                    Triple("matrix-case.properties", "AEAT_TEST", "OFFLINE_SIMULATION"),
                    Triple("matrix-case.properties", "case=registration", "case=wrong"),
                    Triple("matrix-case.properties", "request.sha256=", "request.sha256=wrong"),
                    Triple("outcome.properties", "http.status=200", "http.status=500"),
                    Triple("outcome.properties", "transport.result=XmlResponse", "transport.result=NonXmlResponse"),
                    Triple("network.log", "request.url=https", "request.url=http"),
                    Triple("network.log", "response.url=https", "response.url=http"),
                    Triple("network.log", "delivery=RESPONSE_RECEIVED", "delivery=UNKNOWN"),
                    Triple("network.log", "http.status=200", "http.status=403"),
                    Triple("response.xml", "<RespuestaRegFactuSistemaFacturacion", "<Wrong"),
                    Triple("response.xml", "<TiempoEsperaEnvio>0<", "<TiempoEsperaEnvio>unknown<"),
                    Triple("matrix-timing.properties", "completed.at=2026", "completed.at=2025"),
                    Triple("matrix-timing.properties", "started.at=2026", "started.at=bad"),
                )
            for ((name, old, replacement) in mutations) {
                val file = attempt.resolve(name)
                val original = Files.readString(file)
                assertTrue(old in original, name)
                try {
                    Files.writeString(file, original.replace(old, replacement))
                    assertEquals(MatrixState.REVIEW, observeMatrixCase(session, case).state, name)
                } finally {
                    Files.writeString(file, original)
                }
            }
            val outcome = attempt.resolve("outcome.properties")
            Files.delete(outcome)
            assertEquals(MatrixState.UNKNOWN, observeMatrixCase(session, case).state)
            assertFailsWith<SandboxInputException> { matrixTestSend(session, case, MATRIX_TIME) }
        }

    @Test fun confirmationAndSessionGuardsRunBeforeTransportAndEvidenceCreation() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val case = session.case("pagination-01")
            prepareMatrixCase(session, case, MATRIX_TIME)
            val bundle = loadBundle(session.bundle(case))
            val options =
                mapOf(
                    "run" to session.root.toString(),
                    "case" to case.id,
                    "confirm-sha256" to bundle.sha256,
                    "confirm-records" to "1000",
                )
            for (override in listOf(mapOf("confirm-sha256" to "wrong"), mapOf("confirm-records" to "999"))) {
                assertFailsWith<SandboxInputException> {
                    sendMatrixCase(
                        options + override,
                        { _, _ -> error("No identity access") },
                        { MATRIX_TIME },
                    )
                }
            }
            assertTrue(matrixAttempts(session, case).isEmpty())
            val report =
                commandLineResult(
                    listOf("matrix", "rehearse", "--run", session.root.toString(), "--out", root.resolve("offline").toString()),
                )
            assertEquals(0, report.exitCode)
            val unknown = commandLineResult(listOf("matrix", "unknown", "--run", session.root.toString()))
            assertEquals(2, unknown.exitCode)
            val metadata = session.root.resolve("matrix.properties")
            Files.writeString(metadata, Files.readString(metadata).replace("environment=TEST", "environment=PRODUCTION"))
            assertEquals(2, commandLineResult(listOf("matrix", "report", "--run", session.root.toString())).exitCode)
        }

    @Test fun thrownTransportAndNonXmlRepliesPreserveUncertaintyWithoutAutomaticRetry() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val case = session.case("registration")
            prepareMatrixCase(session, case, MATRIX_TIME)
            val result = matrixTestSend(session, case, MATRIX_TIME) { throw java.io.IOException("PRIVATE") }
            assertEquals(4, result.exitCode)
            assertTrue(!result.output.contains("PRIVATE"))
            assertEquals(MatrixState.UNKNOWN, observeMatrixCase(session, case).state)
            assertEquals(1, matrixAttempts(session, case).size)
        }
}
