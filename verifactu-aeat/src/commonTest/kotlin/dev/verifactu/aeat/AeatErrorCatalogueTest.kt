package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AeatErrorCatalogueTest {
    @Test
    fun classifiesPublishedAcceptedErrorsAsRequiringSubsanation() {
        listOf("2000", "2004", "2009").forEach { code ->
            val incidence = AeatErrorCatalogue.classify(code, "Synthetic diagnostic")

            assertEquals(AeatIncidenceDisposition.ACCEPTED_WITH_ERRORS, incidence.disposition)
            assertEquals(AeatSubsanationRequirement.REQUIRED, incidence.subsanationRequirement)
            assertEquals("AEAT Validaciones y errores", incidence.source?.document)
            assertEquals(AeatErrorCatalogue.VERSION, incidence.source?.version)
            assertEquals("4.3.1 and 4.4", incidence.source?.section)
        }
    }

    @Test
    fun classifiesPublishedRecordAndWholeSubmissionRejectionsWithoutChoosingAWorkflow() {
        val recordRejection = AeatErrorCatalogue.classify("3002")
        val wholeSubmissionRejection = AeatErrorCatalogue.classify("4102")

        assertEquals(AeatIncidenceDisposition.RECORD_REJECTED, recordRejection.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, recordRejection.subsanationRequirement)
        assertEquals(AeatIncidenceDisposition.WHOLE_SUBMISSION_REJECTED, wholeSubmissionRejection.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, wholeSubmissionRejection.subsanationRequirement)
    }

    @Test
    fun keepsFutureCodesInspectableWithoutInventingCorrectionRequirements() {
        val incidence = AeatErrorCatalogue.classify("future-code")

        assertEquals(AeatIncidenceDisposition.UNKNOWN, incidence.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, incidence.subsanationRequirement)
        assertNull(incidence.source)
    }
}
