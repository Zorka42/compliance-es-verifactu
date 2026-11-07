package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.Subsanation
import dev.verifactu.xml.SubmissionRecord
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal val MATRIX_TIME: OffsetDateTime = OffsetDateTime.parse("2026-11-07T12:00:00+01:00")

internal fun testMatrix(root: Path): MatrixSession {
    val party = root.resolve("party.properties")
    writePrivate(party, "nif=89890001K\nname=Synthetic TEST party\n")
    val producer = root.resolve("producer.properties")
    writePrivate(producer, "nif=89890001K\nname=Synthetic TEST producer\n")
    initializeMatrix(
        mapOf("party-file" to party.toString(), "producer-file" to producer.toString(), "out" to root.resolve("run").toString()),
        MATRIX_TIME,
        "12345678-abcd-4321",
    )
    return loadMatrix(root.resolve("run"))
}

internal fun matrixTestSend(
    session: MatrixSession,
    case: MatrixCase,
    time: OffsetDateTime,
    result: (
        (SandboxBundle) -> AeatTransportResult
    )? = null,
): CommandResult {
    val bundle = loadBundle(session.bundle(case))
    return sendMatrixCase(
        mapOf(
            "run" to session.root.toString(),
            "case" to case.id,
            "confirm-sha256" to bundle.sha256,
            "confirm-records" to case.records.toString(),
        ),
        { output, count ->
            assertEquals(case.records, count)
            object : AeatTransportAdapter {
                override fun execute(request: AeatTransportRequest): AeatTransportResult {
                    assertEquals(bundle.plan.soap, request.xmlPayload)
                    val kind =
                        when (case.id) {
                            "warning" -> "warning"
                            "rejection" -> "rejected"
                            "duplicate" -> "duplicate"
                            else -> "accepted"
                        }
                    val response = result?.invoke(bundle) ?: simulatedResponse(bundle, kind)
                    val received = response is AeatTransportResult.XmlResponse
                    writePrivate(
                        output.resolve("network.log"),
                        propertiesText(
                            mapOf(
                                "request.url" to request.endpoint.url,
                                "response.url" to if (received) request.endpoint.url else "unavailable",
                                "delivery" to response.deliveryState.name,
                                "http.status" to if (received) "200" else "unavailable",
                            ),
                        ),
                    )
                    return response
                }
            }
        },
        { time },
    )
}

class MatrixTest {
    @Test fun fullOfflineRehearsalBuildsEveryRealRequestAndKeepsLiveEvidenceEmpty() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val result = rehearseMatrix(session, root.resolve("rehearsal"), MATRIX_TIME)
            assertEquals(0, result.exitCode)
            assertTrue(result.output.contains("10001"))
            assertTrue(result.output.contains("OFFLINE_SIMULATION"))
            assertTrue(MATRIX_CASES.all { observeMatrixCase(session, it).state == MatrixState.PENDING })
            val rehearsal = session.copy(root = root.resolve("rehearsal"))
            val responseSchema = matrixResponseSchema()
            MATRIX_CASES.forEach { case ->
                val xml = Files.readString(rehearsal.directory(case).resolve("synthetic-response.xml"))
                responseSchema.newValidator().validate(
                    javax.xml.transform.stream
                        .StreamSource(xml.reader()),
                )
            }
            assertEquals(
                loadBundle(rehearsal.bundle(session.case("registration"))).sha256,
                loadBundle(rehearsal.bundle(session.case("duplicate"))).sha256,
            )
            val warning = (loadBundle(rehearsal.bundle(session.case("warning"))).plan.record as SubmissionRecord.Registration).value
            assertEquals(ChainState.FirstRecord, warning.draft.chainState)
            val correction = (loadBundle(rehearsal.bundle(session.case("subsanation"))).plan.record as SubmissionRecord.Registration).value
            assertEquals(Subsanation.YES, correction.draft.conditionalData.subsanation)
            assertEquals(warning.hash, (correction.draft.chainState as ChainState.PreviousRecord).hash)
            val first = loadBundle(rehearsal.bundle(session.case("pagination-01")))
            val second = loadBundle(rehearsal.bundle(session.case("pagination-02")))
            assertEquals(1000, first.plan.records.size)
            assertEquals(first.plan.next, (second.plan.record as SubmissionRecord.Registration).value.draft.chainState)
            assertEquals(1, loadBundle(rehearsal.bundle(session.case("pagination-11"))).plan.records.size)
            assertTrue(matrixReport(session).output.contains("INCOMPLETE"))
        }

    @Test fun smallSuiteUsesImmutableBytesExpectedCodesAndManualFlowControl() =
        withWorkspace { root ->
            val session = testMatrix(root)
            var now = MATRIX_TIME
            for (case in MATRIX_CASES.take(6)) {
                prepareMatrixCase(session, case, now)
                val before = loadBundle(session.bundle(case)).sha256
                prepareMatrixCase(session, case, now.plusMinutes(30))
                assertEquals(before, loadBundle(session.bundle(case)).sha256)
                val result =
                    matrixTestSend(session, case, now) { bundle ->
                        val kind =
                            when (case.id) {
                                "warning" -> "warning"
                                "rejection" -> "rejected"
                                "duplicate" -> "duplicate"
                                else -> "accepted"
                            }
                        val reply = simulatedResponse(bundle, kind) as AeatTransportResult.XmlResponse
                        reply.copy(xml = reply.xml.replace("<TiempoEsperaEnvio>0<", "<TiempoEsperaEnvio>60<"))
                    }
                assertEquals(0, result.exitCode, result.output)
                assertEquals(MatrixState.PASS, observeMatrixCase(session, case).state)
                now = now.plusSeconds(60)
                if (case.id == "registration") {
                    val next = session.case("duplicate")
                    prepareMatrixCase(session, next, now)
                    assertFailsWith<SandboxInputException> { matrixTestSend(session, next, now.minusSeconds(1)) }
                    assertTrue(matrixAttempts(session, next).isEmpty())
                }
            }
            assertEquals(listOf("2007"), observeMatrixCase(session, session.case("warning")).codes)
            assertEquals(listOf("1152"), observeMatrixCase(session, session.case("rejection")).codes)
            assertFailsWith<SandboxInputException> { matrixTestSend(session, session.case("registration"), now) }
            assertFalse(matrixReport(session).output.contains("write.matrix=PASS"))
            assertTrue(matrixReport(session).output.contains("flow.control=PASS"))
            val timing = matrixAttempts(session, session.case("duplicate")).single().resolve("matrix-timing.properties")
            Files.writeString(
                timing,
                propertiesText(mapOf("started.at" to MATRIX_TIME.toString(), "completed.at" to MATRIX_TIME.toString())),
            )
            assertTrue(matrixReport(session).output.contains("flow.control=REVIEW"))
        }

    @Test fun notSentCanRepeatSameBytesWhileUnknownDeliveryBlocksFurtherWrites() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val case = session.case("registration")
            prepareMatrixCase(session, case, MATRIX_TIME)
            val digest = loadBundle(session.bundle(case)).sha256
            matrixTestSend(session, case, MATRIX_TIME) { AeatTransportResult.NotSent("Synthetic setup failure") }
            assertEquals(MatrixState.NOT_SENT, observeMatrixCase(session, case).state)
            val networkFile = matrixAttempts(session, case).single().resolve("network.log")
            val network = Files.readString(networkFile)
            Files.writeString(networkFile, network.replace("prewww1.aeat.es", "www1.agenciatributaria.gob.es"))
            assertEquals(MatrixState.REVIEW, observeMatrixCase(session, case).state)
            Files.writeString(networkFile, network)
            matrixTestSend(session, case, MATRIX_TIME) { AeatTransportResult.Timeout("Synthetic timeout") }
            assertEquals(MatrixState.UNKNOWN, observeMatrixCase(session, case).state)
            assertEquals(digest, loadBundle(session.bundle(case)).sha256)
            assertFailsWith<SandboxInputException> { matrixTestSend(session, case, MATRIX_TIME) }
            val paging = session.case("pagination-01")
            prepareMatrixCase(session, paging, MATRIX_TIME)
            assertFailsWith<SandboxInputException> { matrixTestSend(session, paging, MATRIX_TIME) }
        }

    @Test fun negativeResponseMustMatchItsPublishedCodeAndCorrelate() =
        withWorkspace { root ->
            val session = testMatrix(root)
            val registration = session.case("registration")
            prepareMatrixCase(session, registration, MATRIX_TIME)
            matrixTestSend(session, registration, MATRIX_TIME)
            val warning = session.case("warning")
            prepareMatrixCase(session, warning, MATRIX_TIME)
            matrixTestSend(session, warning, MATRIX_TIME) { simulatedResponse(it, "accepted") }
            assertEquals(MatrixState.REVIEW, observeMatrixCase(session, warning).state)
            assertFailsWith<SandboxInputException> { prepareMatrixCase(session, session.case("subsanation"), MATRIX_TIME) }
            val attempt = matrixAttempts(session, registration).single()
            Files.writeString(attempt.resolve("response.xml"), Files.readString(attempt.resolve("response.xml")).replace("-R<", "-OTHER<"))
            assertEquals(MatrixState.REVIEW, observeMatrixCase(session, registration).state)
        }

    @Test fun localCommandsValidateInputsAndDoNotOverwrite() =
        withWorkspace { root ->
            val session = testMatrix(root)
            assertEquals(0, commandLineResult(listOf("matrix", "--help")).exitCode)
            assertEquals(0, commandLineResult(listOf("matrix", "show", "--run", session.root.toString())).exitCode)
            assertEquals(3, commandLineResult(listOf("matrix", "report", "--run", session.root.toString())).exitCode)
            assertEquals(2, commandLineResult(listOf("matrix", "prepare", "--run", session.root.toString(), "--case", "unknown")).exitCode)
            assertEquals(
                2,
                commandLineResult(listOf("matrix", "prepare", "--run", session.root.toString(), "--case", "cancellation")).exitCode,
            )
            val file = session.root.resolve("base.properties")
            Files.writeString(file, Files.readString(file).replace("07-11-2026", "bad"))
            assertEquals(2, commandLineResult(listOf("matrix", "show", "--run", session.root.toString())).exitCode)
        }
}
