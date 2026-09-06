package dev.verifactu.aeat

/**
 * Android transport boundary.
 *
 * The host application injects an adapter backed by its Android-appropriate HTTP and KeyChain
 * integration. This module neither stores certificates nor depends on a JVM HTTP stack.
 */
public class AndroidAeatTransport(
    private val adapter: AeatTransportAdapter,
) {
    /** Delegates a prepared XML request to the Android-specific adapter. */
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
