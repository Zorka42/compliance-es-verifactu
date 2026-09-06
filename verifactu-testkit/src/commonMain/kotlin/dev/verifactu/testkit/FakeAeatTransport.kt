package dev.verifactu.testkit

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult

/**
 * Instance-owned, scripted transport for tests. It performs no I/O or credential access.
 *
 * Each call consumes one result in order. Exhaustion is a test configuration error and
 * throws [IllegalStateException]. This deliberately has no successful default response.
 * Use one instance per test and serialize access; it is not a concurrent application queue.
 */
public class FakeAeatTransport(
    results: List<AeatTransportResult>,
) : AeatTransportAdapter {
    private val scriptedResults: List<AeatTransportResult> = results.toList()
    private val recordedRequests: MutableList<AeatTransportRequest> = mutableListOf()

    /** Snapshot of explicitly captured requests, including XML. Use synthetic test data only. */
    public val requests: List<AeatTransportRequest>
        get() = recordedRequests.toList()

    /** Number of scripted results still available. */
    public val remainingResults: Int
        get() = scriptedResults.size - recordedRequests.size

    /** Captures a request and returns the next scripted outcome without interpreting its XML. */
    override fun execute(request: AeatTransportRequest): AeatTransportResult {
        check(remainingResults > 0) { "FakeAeatTransport has no scripted results remaining." }
        val result = scriptedResults[recordedRequests.size]
        recordedRequests.add(request)
        return result
    }

    /** Summarizes counts without printing captured XML. */
    override fun toString(): String = "FakeAeatTransport(requests=${recordedRequests.size}, remainingResults=$remainingResults)"
}
