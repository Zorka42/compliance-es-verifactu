package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordHashCalculatorTest {
    @Test
    fun retainsAllPinnedAeatGoldenFixtures() {
        listOf(
            HashFixtures.firstRegistration,
            HashFixtures.subsequentRegistration,
            HashFixtures.cancellation,
        ).forEach { fixture ->
            assertEquals(fixture.hash, RecordHashCalculator.sha256(fixture.input), fixture.name)
        }
    }

    @Test
    fun calculatesAeatFirstRegistrationRecordExample() {
        val input =
            RegistrationHashInput(
                issuerId = "89890001K",
                invoiceNumber = "12345678/G33",
                issueDate = "01-01-2024",
                invoiceType = "F1",
                totalTax = "12.35",
                totalAmount = "123.45",
                previousHash = null,
                generatedAt = "2024-01-01T19:20:30+01:00",
            )

        assertEquals(
            "IDEmisorFactura=89890001K&NumSerieFactura=12345678/G33&FechaExpedicionFactura=01-01-2024&TipoFactura=F1&CuotaTotal=12.35&ImporteTotal=123.45&Huella=&FechaHoraHusoGenRegistro=2024-01-01T19:20:30+01:00",
            input.canonicalString(),
        )
        assertEquals(
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60",
            RecordHashCalculator.sha256(input.canonicalString()),
        )
    }

    @Test
    fun calculatesAeatCancellationRecordExample() {
        val input =
            CancellationHashInput(
                cancelledIssuerId = "89890001K",
                cancelledInvoiceNumber = "12345679/G34",
                cancelledIssueDate = "01-01-2024",
                previousHash = "F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97",
                generatedAt = "2024-01-01T19:20:40+01:00",
            )

        assertEquals(
            "177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68",
            RecordHashCalculator.sha256(input.canonicalString()),
        )
    }
}
