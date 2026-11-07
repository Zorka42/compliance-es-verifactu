package dev.verifactu.tools.query

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeQueryProcessTest {
    @Test
    fun validatesProcessReceiptsAndKeepsIdentityReferencesOutOfDiagnostics() =
        workspace { root ->
            val call = call(root)
            val receipt =
                executeNativeQuery(
                    helper(root, "kind=XML_RESPONSE\ndelivery=RESPONSE_RECEIVED\nhttp.status=200\nidentity.sha256=$IDENTITY"),
                    call.copy(issuer = "AC FNMT Usuarios"),
                )
            assertEquals("XML_RESPONSE", receipt.kind)
            assertEquals("AC FNMT Usuarios", queryProperties(root.resolve("native.properties"))["selected.issuer"])
            assertEquals(TEST_QUERY_ENDPOINT, receipt.responseUrl)
            assertEquals(200, receipt.httpStatus)
            assertEquals(IDENTITY, receipt.identity)
            assertFalse(receipt.toString().contains(IDENTITY))
            assertFalse(call.copy(identity = IDENTITY).toString().contains(IDENTITY))
            assertFailsWith<QueryInputException> { installedHelper() }
        }

    @Test
    fun cancellationAndMissingIdentityStayNotSentEvenDuringPaging() =
        workspace { root ->
            for (kind in listOf("SELECTION_CANCELLED", "IDENTITY_UNAVAILABLE")) {
                val helper = helper(root, "kind=$kind\ndelivery=NOT_SENT\nhttp.status=unavailable\nidentity.sha256=unavailable")
                val receipt = executeNativeQuery(helper, call(root).copy(identity = IDENTITY))
                assertEquals("NOT_SENT", receipt.delivery)
                assertEquals(kind, receipt.kind)
                assertEquals(null, receipt.identity)
            }
        }

    @Test
    fun refusesCorruptContradictoryAndCrossIdentityReceipts() =
        workspace { root ->
            val invalid =
                listOf(
                    "kind=PRIVATE\ndelivery=RESPONSE_RECEIVED\nhttp.status=200",
                    "",
                    "kind=XML_RESPONSE\ndelivery=NOT_SENT\nhttp.status=200",
                    "kind=XML_RESPONSE\ndelivery=RESPONSE_RECEIVED\nhttp.status=999",
                    "kind=XML_RESPONSE\ndelivery=RESPONSE_RECEIVED\nhttp.status=unavailable",
                    "kind=XML_RESPONSE\nkind=OTHER\ndelivery=UNKNOWN",
                    "kind=XML_RESPONSE\ndelivery=RESPONSE_RECEIVED\nhttp.status=200\nidentity.sha256=${"b".repeat(64)}",
                )
            invalid.forEach { text ->
                val receipt = executeNativeQuery(helper(root, text), call(root).copy(identity = IDENTITY))
                assertEquals("INVALID_HELPER_RECEIPT", receipt.kind)
                assertEquals("UNKNOWN", receipt.delivery)
                assertFalse(receipt.toString().contains("PRIVATE"))
            }
            val wrongDigest = helper(root, "kind=XML_RESPONSE\ndelivery=RESPONSE_RECEIVED\nhttp.status=200", digest = "wrong")
            assertEquals("UNKNOWN", executeNativeQuery(wrongDigest, call(root)).delivery)
        }

    @Test
    fun refusesMissingAndDifferentNativeRoutesWithoutRepeatingUntrustedUrls() =
        workspace { root ->
            val properties = "kind=XML_RESPONSE\ndelivery=RESPONSE_RECEIVED\nhttp.status=200"
            val production = "https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
            for (route in listOf(null, "unavailable", production, "$TEST_QUERY_ENDPOINT?PRIVATE")) {
                val response = executeNativeQuery(helper(root, properties, responseUrl = route), call(root))
                assertEquals("INVALID_HELPER_RECEIPT", response.kind)
                assertEquals(null, response.responseUrl)
                val request = executeNativeQuery(helper(root, properties, requestUrl = route), call(root))
                assertEquals("INVALID_HELPER_RECEIPT", request.kind)
            }
            val unknown =
                executeNativeQuery(
                    helper(root, "kind=UNKNOWN_DELIVERY\ndelivery=UNKNOWN", responseUrl = TEST_QUERY_ENDPOINT),
                    call(root),
                )
            assertEquals("UNKNOWN", unknown.delivery)
            assertEquals(TEST_QUERY_ENDPOINT, unknown.responseUrl)
        }

    @Test
    fun failedProcessAndMissingReceiptRemainUnknownWhileFailedLaunchIsNotSent() =
        workspace { root ->
            assertEquals("NOT_SENT", executeNativeQuery(root.resolve("absent"), call(root)).delivery)
            val failed = root.resolve("failed")
            script(failed, "#!/bin/sh\nexit 7\n")
            assertEquals("UNKNOWN", executeNativeQuery(failed, call(root)).delivery)
            script(failed, "#!/bin/sh\nexit 0\n")
            assertEquals("UNKNOWN", executeNativeQuery(failed, call(root)).delivery)
        }

    @Test
    fun interruptedProcessIsTerminatedWithoutRetryAndRestoresInterruption() =
        workspace { root ->
            val helper = root.resolve("wait")
            script(helper, "#!/bin/sh\nexec /bin/sleep 30\n")
            try {
                Thread.currentThread().interrupt()
                val result = executeNativeQuery(helper, call(root))
                assertEquals("UNKNOWN", result.delivery)
                assertTrue(Thread.currentThread().isInterrupted)
            } finally {
                Thread.interrupted()
            }
        }

    @Test
    fun privateInputsRejectLinksDuplicateKeysMalformedUtf8AndOversize() =
        workspace { root ->
            val input = root.resolve("data")
            Files.writeString(input, "nif=one\nnif=two\n")
            assertFailsWith<QueryInputException> { queryProperties(input) }
            Files.writeString(input, "value=\\uZZZZ")
            assertFailsWith<QueryInputException> { queryProperties(input) }
            Files.write(input, byteArrayOf(0xC3.toByte(), 0x28))
            assertFailsWith<QueryInputException> { readPrivateInput(input, 10) }
            Files.writeString(input, "x".repeat(17_000))
            assertFailsWith<QueryInputException> { queryProperties(input) }
            Files.createSymbolicLink(root.resolve("link"), input)
            assertFailsWith<QueryInputException> { readPrivateInput(root.resolve("link"), 10) }
            assertFailsWith<QueryInputException> { readPrivateInput(root, 10) }
        }

    @Test
    fun unreadableReceiptStaysUnknownAndAbsentOptionalHttpStatusIsValidForCancellation() =
        workspace { root ->
            val cancelled = executeNativeQuery(helper(root, "kind=SELECTION_CANCELLED\ndelivery=NOT_SENT"), call(root))
            assertEquals("NOT_SENT", cancelled.delivery)
            val call = call(root)
            val receipt = root.resolve("native.properties")
            Files.writeString(receipt, "private")
            Files.setPosixFilePermissions(receipt, PosixFilePermissions.fromString("---------"))
            val executable = root.resolve("noop")
            script(executable, "#!/bin/sh\nexit 0\n")
            try {
                assertEquals("UNKNOWN", executeNativeQuery(executable, call).delivery)
            } finally {
                Files.setPosixFilePermissions(receipt, PosixFilePermissions.fromString("rw-------"))
            }
        }

    private fun call(root: Path): NativeQueryCall {
        Files.deleteIfExists(root.resolve("native.properties"))
        return NativeQueryCall(root.resolve("request.xml"), root, "d".repeat(64), TEST_QUERY_ENDPOINT, null)
    }

    private fun helper(
        root: Path,
        properties: String,
        digest: String? = null,
        requestUrl: String? = TEST_QUERY_ENDPOINT,
        responseUrl: String? = if (properties.contains("delivery=RESPONSE_RECEIVED")) TEST_QUERY_ENDPOINT else "unavailable",
    ): Path {
        val path = root.resolve("helper")
        val selectedDigest = digest ?: "${'$'}digest"
        val routes = listOfNotNull(requestUrl?.let { "request.url=$it" }, responseUrl?.let { "response.url=$it" }).joinToString("\n")
        script(
            path,
            """
            #!/bin/sh
            while [ "${'$'}#" -gt 0 ]; do
              key="${'$'}1"
              value="${'$'}2"
              shift 2
              case "${'$'}key" in
                --result) result="${'$'}value" ;;
                --sha256) digest="${'$'}value" ;;
                --issuer) issuer="${'$'}value" ;;
              esac
            done
            cat > "${'$'}result" <<EOF
            """.trimIndent() +
                "\n$properties\nrequest.sha256=$selectedDigest\n$routes\nselected.issuer=${'$'}issuer\nEOF\n",
        )
        return path
    }

    private fun script(
        path: Path,
        text: String,
    ) {
        Files.writeString(path, text)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
    }

    private fun workspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("verifactu-query-process-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val IDENTITY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
