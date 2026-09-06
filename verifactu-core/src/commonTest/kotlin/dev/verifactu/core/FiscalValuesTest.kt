package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FiscalValuesTest {
    @Test
    fun parsesValuesWithinPinnedAeatSchemaLimits() {
        assertEquals("89890001K", (TaxIdentifier.parse("89890001k") as ValueResult.Valid).value.value)
        assertEquals("12345678/G33", (InvoiceNumber.parse(" 12345678/G33 ") as ValueResult.Valid).value.value)
        assertEquals("29-02-2024", (InvoiceIssueDate.parse("29-02-2024") as ValueResult.Valid).value.value)
        assertEquals("+123.4", (FiscalAmount.parse("+123.4") as ValueResult.Valid).value.value)
        assertEquals(
            "2024-01-01T19:20:30+01:00",
            (RecordGenerationTimestamp.parse("2024-01-01T19:20:30+01:00") as ValueResult.Valid).value.value,
        )
    }

    @Test
    fun rejectsValuesOutsidePinnedAeatSchemaLimits() {
        assertIs<ValueResult.Invalid>(TaxIdentifier.parse("89890001"))
        assertIs<ValueResult.Invalid>(InvoiceNumber.parse(""))
        assertIs<ValueResult.Invalid>(InvoiceIssueDate.parse("29-02-2023"))
        assertIs<ValueResult.Invalid>(FiscalAmount.parse("1,20"))
        assertIs<ValueResult.Invalid>(RecordGenerationTimestamp.parse("2024-01-01T19:20:30Z"))
    }

    @Test
    fun preservesValidTimestampRepresentationsAtCalendarAndOffsetBoundaries() {
        listOf(
            "2000-02-29T23:59:59+13:59",
            "1900-02-28T00:00:00-00:00",
            "2024-02-29T24:00:00+14:00",
            "2024-12-31T24:00:00-14:00",
        ).forEach { value ->
            assertEquals(value, assertIs<ValueResult.Valid<RecordGenerationTimestamp>>(RecordGenerationTimestamp.parse(value)).value.value)
        }
    }

    @Test
    fun rejectsImpossibleTimestampsBeforeRecordCreation() {
        listOf(
            "0000-01-01T00:00:00+00:00",
            "1900-02-29T00:00:00+00:00",
            "2023-02-29T00:00:00+00:00",
            "2024-02-30T00:00:00+00:00",
            "2024-00-01T00:00:00+00:00",
            "2024-13-01T00:00:00+00:00",
            "2024-01-00T00:00:00+00:00",
            "2024-04-31T00:00:00+00:00",
            "2024-01-01T25:00:00+00:00",
            "2024-01-01T24:01:00+00:00",
            "2024-01-01T24:00:01+00:00",
            "2024-01-01T00:60:00+00:00",
            "2024-01-01T00:00:60+00:00",
            "2024-01-01T00:00:00+14:01",
            "2024-01-01T00:00:00-14:01",
            "2024-01-01T00:00:00+15:00",
            "2024-01-01T00:00:00+00:60",
            "2024-99-99T99:99:99+99:99",
        ).forEach { value ->
            assertIs<ValueResult.Invalid>(RecordGenerationTimestamp.parse(value), value)
        }
    }

    @Test
    fun rejectsNonXmlCharactersInIdentifiers() {
        listOf('\u0000', '\u000B', '\uD800', '\uDC00', '\uFFFE', '\uFFFF').forEach { character ->
            assertIs<ValueResult.Invalid>(TaxIdentifier.parse("AB${character}123456"))
            assertIs<ValueResult.Invalid>(InvoiceNumber.parse("INV${character}1"))
        }
    }

    @Test
    fun measuresSchemaTextLengthsInUnicodeCharacters() {
        val supplementaryCharacter = "\uD83D\uDE00"
        assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse("A${supplementaryCharacter}1234567"))
        assertIs<ValueResult.Valid<InvoiceNumber>>(InvoiceNumber.parse(supplementaryCharacter.repeat(60)))
        assertIs<ValueResult.Invalid>(InvoiceNumber.parse(supplementaryCharacter.repeat(61)))
        assertEquals("INV\r\n\t1", assertIs<ValueResult.Valid<InvoiceNumber>>(InvoiceNumber.parse("INV\r\n\t1")).value.value)
    }
}
