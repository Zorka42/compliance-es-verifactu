package dev.verifactu.tools.query

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceQueryTest {
    private val query = InvoiceQuery()
    private val party = QueryParty("89890001K", "Synthetic taxpayer")
    private val period = QueryPeriod("2026", "09")

    @Test
    fun validatesIssuedAndReceivedRequestsWithUnicodeAndEscapingAgainstOfficialSchemas() {
        QueryDirection.entries.forEach { direction ->
            val request = query.prepare(direction, party.copy(name = "Peña & <Example>\r😀"), period)
            val root = QueryXml().parse(request, QUERY_NAMESPACE, "ConsultaFactuSistemaFacturacion")
            val identity = root.required(QUERY_NAMESPACE, "Cabecera").required(COMMON_NAMESPACE, direction.xmlName)
            assertEquals("Peña & <Example>\r😀", identity.required(COMMON_NAMESPACE, "NombreRazon").textContent)
            assertEquals(request, query.prepare(direction, party.copy(name = "Peña & <Example>\r😀"), period))
            assertFalse(request.contains("MostrarSistemaInformatico"))
        }
        listOf(QueryPeriod("2026", "13"), QueryPeriod("26", "09")).forEach {
            assertFailsWith<QueryInputException> { query.prepare(QueryDirection.ISSUED, party, it) }
        }
        assertFailsWith<QueryInputException> { query.prepare(QueryDirection.ISSUED, party.copy(nif = "bad"), period) }
        assertFailsWith<QueryInputException> { query.prepare(QueryDirection.ISSUED, party.copy(name = "x\u0001"), period) }
    }

    @Test
    fun parsesEmptyResultsAndAllStoredStatesIncludingSparseCancellation() {
        val empty = query.inspect(fixture("issued-empty.xml"))
        assertEquals(QueryDirection.ISSUED, empty.direction)
        assertTrue(empty.records.isEmpty())
        assertNull(empty.next)
        val page = query.inspect(fixture("received-page.xml"))
        assertEquals(QueryDirection.RECEIVED, page.direction)
        assertEquals(listOf("Correcto", "AceptadoConErrores", "Anulado"), page.records.map { it.status })
        assertEquals(
            "SUP-A&B",
            page.records
                .first()
                .invoice.number,
        )
        assertNull(page.records.last().total)
        assertEquals("SUP-3", page.next?.number)
        assertFalse(page.toString().contains(party.nif))
        assertFalse(
            page.records
                .first()
                .toString()
                .contains("SUP-"),
        )
    }

    @Test
    fun continuesUsingReturnedCursorAndRejectsCrossQueryReuse() {
        val page = query.inspect(fixture("received-page.xml"))
        val request = query.prepare(QueryDirection.RECEIVED, party, period, page)
        val root = QueryXml().parse(request, QUERY_NAMESPACE, "ConsultaFactuSistemaFacturacion")
        assertEquals(
            "SUP-3",
            root
                .required(QUERY_NAMESPACE, "FiltroConsulta")
                .required(QUERY_NAMESPACE, "ClavePaginacion")
                .required(COMMON_NAMESPACE, "NumSerieFactura")
                .textContent,
        )
        assertFailsWith<QueryInputException> { query.prepare(QueryDirection.ISSUED, party, period, page) }
        assertFailsWith<QueryInputException> { query.prepare(QueryDirection.RECEIVED, party.copy(nif = "89890002Q"), period, page) }
        assertFailsWith<QueryInputException> { query.prepare(QueryDirection.RECEIVED, party, period.copy(month = "08"), page) }
        assertFailsWith<QueryInputException> {
            query.prepare(
                QueryDirection.RECEIVED,
                party,
                period,
                query.inspect(fixture("received-final.xml")),
            )
        }
    }

    @Test
    fun rejectsUnsafeMalformedWrongNamespaceAndInconsistentDocuments() {
        val empty = fixture("issued-empty.xml").substringAfter("?>")
        val page = fixture("received-page.xml")
        val invalid =
            listOf(
                "<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///nonexistent-verifactu-test'>]>$empty",
                empty.replace(RESPONSE_NAMESPACE, "urn:wrong"),
                empty.dropLast(30),
                page.replace("<r:IndicadorPaginacion>S", "<r:IndicadorPaginacion>N"),
                empty.replace("SinDatos", "ConDatos"),
                page.replace(">Anulado<", ">Incorrecto<"),
                "<soap:Envelope xmlns:soap=\"$SOAP_NAMESPACE\"><soap:Body>$empty</soap:Body><soap:Body>$empty</soap:Body></soap:Envelope>",
                "<soap:Envelope xmlns:soap=\"$SOAP_NAMESPACE\"><soap:Body>$empty$empty</soap:Body></soap:Envelope>",
                "<x>".repeat(65) + "</x>".repeat(65),
            )
        invalid.forEach { assertFailsWith<QueryInputException> { query.inspect(it) } }
        val envelope = "<soap:Envelope xmlns:soap=\"$SOAP_NAMESPACE\"><soap:Body>$empty</soap:Body></soap:Envelope>"
        assertEquals(0, query.inspect(envelope).records.size)
    }

    @Test
    fun commandUsesExplicitLocalFilesRedactsByDefaultAndDoesNotOverwriteOutputs() {
        val directory = Files.createTempDirectory("verifactu-query-test")
        try {
            val identity = directory.resolve("party.properties")
            val input = directory.resolve("response.xml")
            val output = directory.resolve("request.xml")
            Files.writeString(identity, "nif=89890001K\nname=Synthetic taxpayer\n")
            Files.writeString(input, fixture("received-page.xml"))
            val args =
                listOf(
                    "prepare",
                    "--direction",
                    "received",
                    "--party-file",
                    identity.toString(),
                    "--year",
                    "2026",
                    "--month",
                    "09",
                    "--out",
                    output.toString(),
                    "--page-from",
                    input.toString(),
                )
            assertTrue(runCommand(args).contains("No request was sent"))
            assertTrue(Files.readString(output).contains("SUP-3"))
            assertFailsWith<java.nio.file.FileAlreadyExistsException> { runCommand(args) }
            val summary = runCommand(listOf("inspect", "--input", input.toString()))
            assertFalse(summary.contains("89890001K"))
            assertFalse(summary.contains("SUP-A"))
            assertTrue(runCommand(listOf("inspect", "--input", input.toString(), "--details")).contains("SUP-A&B"))
            assertFailsWith<QueryInputException> { runCommand(listOf("inspect", "--input", input.toString(), "--send", "true")) }
        } finally {
            Files.list(directory).use { files -> files.forEach { Files.delete(it) } }
            Files.delete(directory)
        }
    }

    @Test
    fun everyArchivedContractMatchesItsRecordedDigest() {
        val rows = checkNotNull(javaClass.classLoader.getResourceAsStream("manifest.tsv")).bufferedReader().use { it.readLines() }
        assertTrue(rows.size >= 11)
        rows.drop(1).forEach { row ->
            val columns = row.split('\t')
            val bytes = checkNotNull(javaClass.classLoader.getResourceAsStream(columns[0])).use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(columns[1], digest, columns[0])
            assertTrue(columns[3].startsWith("https://"))
        }
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("query-fixtures/$name"))
            .bufferedReader()
            .use { it.readText() }
}
