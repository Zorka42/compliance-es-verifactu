package dev.verifactu.aeat

/** A prepared XML transport request that does not contain credentials. */
public data class AeatTransportRequest(
    public val endpoint: AeatSubmissionEndpoint,
    public val xmlPayload: String,
)

/** Platform-provided executor for an AEAT transport request. */
public interface AeatTransportAdapter {
    /** Executes a prepared request without exposing certificate material to common code. */
    public fun execute(request: AeatTransportRequest): AeatTransportResult
}
