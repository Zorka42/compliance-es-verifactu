package dev.verifactu.aeat

/**
 * iOS and macOS transport boundary for a caller-owned Apple networking adapter.
 *
 * The adapter owns request execution and client authentication. This class performs no
 * certificate lookup, Keychain access, scheduling, or logging. Call blocking adapters on
 * a worker thread, never the Apple main thread.
 */
public class AppleAeatTransport(
    private val adapter: AeatTransportAdapter,
) {
    /** Rejects non-HTTPS destinations before delegating the prepared XML request. */
    public fun submit(
        endpoint: AeatSubmissionEndpoint,
        xmlPayload: String,
    ): AeatTransportResult {
        if (!endpoint.url.startsWith("https://")) {
            return AeatTransportResult.InvalidEndpoint("The AEAT endpoint must use HTTPS.")
        }
        return adapter.execute(AeatTransportRequest(endpoint, xmlPayload))
    }
}
