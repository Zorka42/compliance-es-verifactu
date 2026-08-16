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
}
