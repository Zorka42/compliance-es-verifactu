package dev.verifactu.aeat

/** Transport evidence only; receiving a response does not establish fiscal acceptance. */
public enum class AeatDeliveryState {
    /** Local validation or request setup failed before network delivery was attempted. */
    NOT_SENT,

    /** An HTTP response was received; callers must still interpret it. */
    RESPONSE_RECEIVED,

    /** The available evidence cannot determine whether the fiscal payload was delivered. */
    UNKNOWN,
}

/** Outcome of one attempted delivery of an AEAT SOAP payload. */
public sealed interface AeatTransportResult {
    /** Conservative delivery evidence, independent of AEAT acceptance or retry policy. */
    public val deliveryState: AeatDeliveryState
        get() =
            when (this) {
                is InvalidEndpoint, is NotSent -> AeatDeliveryState.NOT_SENT
                is XmlResponse, is NonXmlResponse, is ResponseTooLarge -> AeatDeliveryState.RESPONSE_RECEIVED
                is Timeout, is NetworkFailure, is UnknownDelivery -> AeatDeliveryState.UNKNOWN
            }

    /** An HTTP response whose body declares an XML media type, including non-success status codes. */
    public data class XmlResponse(
        public val statusCode: Int,
        public val contentType: String?,
        public val xml: String,
    ) : AeatTransportResult {
        /** Omits raw XML and remote media-type text from diagnostic output. */
        override fun toString(): String = "XmlResponse(statusCode=$statusCode, xml=redacted)"
    }

    /** The configured endpoint is not suitable for secure AEAT delivery. */
    public data class InvalidEndpoint(
        public val reason: String,
    ) : AeatTransportResult {
        /** Omits caller-provided diagnostic text. */
        override fun toString(): String = "InvalidEndpoint(reason=redacted)"
    }

    /** Local request setup failed before the transport invoked network delivery. */
    public data class NotSent(
        public val reason: String,
    ) : AeatTransportResult {
        /** Omits caller-provided diagnostic text. */
        override fun toString(): String = "NotSent(reason=redacted)"
    }

    /** The request exceeded the caller-configured timeout; delivery may have occurred. */
    public data class Timeout(
        public val reason: String,
    ) : AeatTransportResult {
        /** Omits caller-provided diagnostic text. */
        override fun toString(): String = "Timeout(reason=redacted)"
    }

    /** No response was available. This legacy result cannot prove that the request was not sent. */
    public data class NetworkFailure(
        public val reason: String,
    ) : AeatTransportResult {
        /** Omits caller-provided diagnostic text. */
        override fun toString(): String = "NetworkFailure(reason=redacted)"
    }

    /**
     * The request may have reached AEAT, but the host could not determine its delivery outcome.
     *
     * Callers must reconcile this result using the fiscal-record correlation data before deciding
     * whether a new submission is appropriate.
     */
    public data class UnknownDelivery(
        public val reason: String,
    ) : AeatTransportResult {
        /** Omits caller-provided diagnostic text. */
        override fun toString(): String = "UnknownDelivery(reason=redacted)"
    }

    /** AEAT or an intermediary returned a response that was not XML. */
    public data class NonXmlResponse(
        public val statusCode: Int,
        public val contentType: String?,
    ) : AeatTransportResult {
        /** Omits remote media-type text from diagnostic output. */
        override fun toString(): String = "NonXmlResponse(statusCode=$statusCode, contentType=redacted)"
    }

    /** An HTTP response whose body exceeded the configured local safety limit before it was retained. */
    public data class ResponseTooLarge(
        public val statusCode: Int,
        public val contentType: String?,
        public val maxBodyBytes: Int,
    ) : AeatTransportResult {
        /** Omits remote media-type text and body bytes from diagnostic output. */
        override fun toString(): String = "ResponseTooLarge(statusCode=$statusCode, maxBodyBytes=$maxBodyBytes)"
    }
}
