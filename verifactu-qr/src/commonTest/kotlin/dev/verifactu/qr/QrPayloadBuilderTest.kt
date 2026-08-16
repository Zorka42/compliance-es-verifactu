package dev.verifactu.qr

import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.ValueResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QrPayloadBuilderTest {
    @Test
    fun createsTheOfficialTestEnvironmentUrlInRequiredParameterOrder() {
        val result = QrPayloadBuilder.build(QrPayloadInput(invoice(), amount("241.4"), QrEnvironment.TEST))

        assertEquals(
            "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=89890001K&numserie=12345678-G33&fecha=01-09-2024&importe=241.4",
            assertIs<QrPayloadResult.Created>(result).payload.url,
        )
    }

    @Test
    fun encodesReservedInvoiceNumberCharacters() {
        val escaped = QrPayloadBuilder.build(QrPayloadInput(invoice("12345678&G33"), amount("241.4"), QrEnvironment.TEST))

        assertEquals(true, assertIs<QrPayloadResult.Created>(escaped).payload.url.contains("numserie=12345678%26G33"))
    }

    @Test
    fun rejectsNonAsciiInvoiceNumbersWithTypedValidationIssues() {
        val result = QrPayloadBuilder.build(QrPayloadInput(invoice("Factura-ñ"), amount("241.4"), QrEnvironment.TEST))

        assertEquals(
            "VF-QR-001",
            assertIs<QrPayloadResult.Invalid>(result)
                .report.issues
                .single()
                .code,
        )
    }

    private fun invoice(number: String = "12345678-G33"): InvoiceIdentifier =
        InvoiceIdentifier(
            (TaxIdentifier.parse("89890001K") as ValueResult.Valid).value,
            (InvoiceNumber.parse(number) as ValueResult.Valid).value,
            (InvoiceIssueDate.parse("01-09-2024") as ValueResult.Valid).value,
        )

    private fun amount(value: String): FiscalAmount = (FiscalAmount.parse(value) as ValueResult.Valid).value
}
