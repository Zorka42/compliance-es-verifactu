package dev.verifactu.aeat

/** Outcome of one attempted delivery of an AEAT SOAP payload. */
public sealed interface AeatTransportResult {
    /** An HTTP response whose body declares an XML media type, including non-success status codes. */
    public data class XmlResponse(
        public val statusCode: Int,
        public val contentType: String?,
        public val xml: String,
    ) : AeatTransportResult

    /** The configured endpoint is not suitable for secure AEAT delivery. */
    public data class InvalidEndpoint(
        public val reason: String,
    ) : AeatTransportResult

    /** The request exceeded the caller-configured timeout; delivery may have occurred. */
    public data class Timeout(
        public val reason: String,
    ) : AeatTransportResult

    /** No response was available. This legacy result cannot prove that the request was not sent. */
    public data class NetworkFailure(
        public val reason: String,
    ) : AeatTransportResult

    /** AEAT or an intermediary returned a response that was not XML. */
    public data class NonXmlResponse(
        public val statusCode: Int,
        public val contentType: String?,
    ) : AeatTransportResult
}
