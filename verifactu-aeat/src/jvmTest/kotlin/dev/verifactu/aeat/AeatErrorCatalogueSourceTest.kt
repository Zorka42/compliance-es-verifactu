package dev.verifactu.aeat

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AeatErrorCatalogueSourceTest {
    @Test
    fun coversEveryArchivedCodeWithTheDispositionFromItsOfficialSection() {
        val entries = archivedEntries()
        assertEquals(247, entries.size, "Review the archived catalogue baseline when its code set changes.")
        entries.forEach { (code, section) ->
            val record = AeatErrorCatalogue.classify(code, location = AeatErrorLocation.RECORD)
            assertNotNull(record.source, "Missing source-backed classification for code $code.")
            when (section) {
                CatalogueSection.WHOLE_SUBMISSION_REJECTION -> {
                    assertEquals(AeatIncidenceDisposition.WHOLE_SUBMISSION_REJECTED, record.disposition, code)
                    assertEquals(AeatSubsanationRequirement.UNKNOWN, record.subsanationRequirement, code)
                }
                CatalogueSection.LOCATION_DEPENDENT_REJECTION -> {
                    assertEquals(AeatIncidenceDisposition.RECORD_REJECTED, record.disposition, code)
                    assertEquals(AeatSubsanationRequirement.UNKNOWN, record.subsanationRequirement, code)
                    assertEquals(AeatIncidenceDisposition.UNKNOWN, AeatErrorCatalogue.classify(code).disposition, code)
                    assertEquals(
                        AeatIncidenceDisposition.WHOLE_SUBMISSION_REJECTED,
                        AeatErrorCatalogue.classify(code, location = AeatErrorLocation.HEADER).disposition,
                        code,
                    )
                }
                CatalogueSection.ACCEPTED_WITH_ERRORS -> {
                    assertEquals(AeatIncidenceDisposition.ACCEPTED_WITH_ERRORS, record.disposition, code)
                    assertEquals(AeatSubsanationRequirement.REQUIRED, record.subsanationRequirement, code)
                }
            }
        }
    }

    @Test
    fun doesNotInventMappingsForUnlistedFourDigitCodes() {
        val known = archivedEntries().keys
        for (value in 0..9999) {
            val code = value.toString().padStart(4, '0')
            if (code in known) continue
            val incidence = AeatErrorCatalogue.classify(code, location = AeatErrorLocation.RECORD)
            assertEquals(AeatIncidenceDisposition.UNKNOWN, incidence.disposition, code)
            assertEquals(AeatSubsanationRequirement.UNKNOWN, incidence.subsanationRequirement, code)
            assertNull(incidence.source, code)
        }
    }

    private fun archivedEntries(): Map<String, CatalogueSection> {
        val stream = assertNotNull(javaClass.getResourceAsStream("/errores.properties"))
        val lines = stream.bufferedReader(StandardCharsets.ISO_8859_1).use { it.readLines() }
        val entries = linkedMapOf<String, CatalogueSection>()
        var section: CatalogueSection? = null
        for (line in lines) {
            if (line.startsWith("*********")) {
                section =
                    when {
                        line.contains("rechazo del envío completo") -> CatalogueSection.WHOLE_SUBMISSION_REJECTION
                        line.contains("rechazo de la factura") -> CatalogueSection.LOCATION_DEPENDENT_REJECTION
                        line.contains("aceptación del registro de facturación") -> CatalogueSection.ACCEPTED_WITH_ERRORS
                        else -> error("Unrecognized official catalogue section; review the source baseline.")
                    }
            } else {
                val code = CODE_LINE.find(line)?.groupValues?.get(1) ?: continue
                assertNull(entries.put(code, assertNotNull(section)), "Duplicate archived code $code.")
            }
        }
        return entries
    }
}

private enum class CatalogueSection {
    WHOLE_SUBMISSION_REJECTION,
    LOCATION_DEPENDENT_REJECTION,
    ACCEPTED_WITH_ERRORS,
}

private val CODE_LINE: Regex = Regex("^(\\d{4})\\s*=")
