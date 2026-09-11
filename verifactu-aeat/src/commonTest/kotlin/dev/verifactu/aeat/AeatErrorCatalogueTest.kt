package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AeatErrorCatalogueTest {
    @Test
    fun classifiesPublishedAcceptedErrorsAsRequiringSubsanation() {
        listOf("2000", "2004", "2009").forEach { code ->
            val incidence = AeatErrorCatalogue.classify(code, "Synthetic diagnostic")

            assertEquals(AeatIncidenceDisposition.ACCEPTED_WITH_ERRORS, incidence.disposition)
            assertEquals(AeatSubsanationRequirement.REQUIRED, incidence.subsanationRequirement)
            assertEquals("AEAT Validaciones y errores; errores.properties", incidence.source?.document)
            assertEquals(AeatErrorCatalogue.VERSION, incidence.source?.version)
            assertEquals("4.3.1 and 4.4", incidence.source?.section)
        }
    }

    @Test
    fun classifiesPublishedRecordAndWholeSubmissionRejectionsWithoutChoosingAWorkflow() {
        val recordRejection = AeatErrorCatalogue.classify("3002", location = AeatErrorLocation.RECORD)
        val wholeSubmissionRejection = AeatErrorCatalogue.classify("4102")

        assertEquals(AeatIncidenceDisposition.RECORD_REJECTED, recordRejection.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, recordRejection.subsanationRequirement)
        assertEquals(AeatIncidenceDisposition.WHOLE_SUBMISSION_REJECTED, wholeSubmissionRejection.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, wholeSubmissionRejection.subsanationRequirement)
    }

    @Test
    fun requiresLocationForCodesWhoseRejectionScopeDependsOnTheHeader() {
        listOf("1100", "1200", "3002").forEach { code ->
            assertEquals(AeatIncidenceDisposition.UNKNOWN, AeatErrorCatalogue.classify(code).disposition)
            val header = AeatErrorCatalogue.classify(code, location = AeatErrorLocation.HEADER)
            val record = AeatErrorCatalogue.classify(code, location = AeatErrorLocation.RECORD)
            assertEquals(AeatIncidenceDisposition.WHOLE_SUBMISSION_REJECTED, header.disposition)
            assertEquals(AeatIncidenceDisposition.RECORD_REJECTED, record.disposition)
            assertEquals(AeatErrorLocation.HEADER, header.location)
            assertEquals(AeatSubsanationRequirement.UNKNOWN, header.subsanationRequirement)
        }
    }

    @Test
    fun omitsRemoteTextFromSummariesWhileRetainingExplicitDiagnostics() {
        val privateText = "PRIVATE-MARKER"
        val incidence = AeatErrorCatalogue.classify(privateText, privateText)
        assertFalse(incidence.toString().contains(privateText))
        assertEquals(privateText, incidence.code)
        assertEquals(privateText, incidence.description)
    }

    @Test
    fun keepsFutureCodesInspectableWithoutInventingCorrectionRequirements() {
        val incidence = AeatErrorCatalogue.classify("future-code")

        assertEquals(AeatIncidenceDisposition.UNKNOWN, incidence.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, incidence.subsanationRequirement)
        assertNull(incidence.source)
    }
}
