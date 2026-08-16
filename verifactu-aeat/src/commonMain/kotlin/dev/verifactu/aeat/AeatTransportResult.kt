package dev.verifactu.aeat

/** Outcome of one attempted delivery of an AEAT SOAP payload. */
public sealed interface AeatTransportResult {
    /** A successful HTTP response whose body declares an XML media type. */
    public data class XmlResponse(
        public val statusCode: Int,
        public val contentType: String?,
        public val xml: String,
    ) : AeatTransportResult

    /** The configured endpoint is not suitable for secure AEAT delivery. */
    public data class InvalidEndpoint(
        public val reason: String,
    ) : AeatTransportResult

    /** The request exceeded the caller-configured timeout. */
    public data class Timeout(
        public val reason: String,
    ) : AeatTransportResult

    /** The connection failed before a response was available. */
    public data class NetworkFailure(
        public val reason: String,
    ) : AeatTransportResult

    /** AEAT or an intermediary returned a response that was not XML. */
    public data class NonXmlResponse(
        public val statusCode: Int,
        public val contentType: String?,
    ) : AeatTransportResult
}
