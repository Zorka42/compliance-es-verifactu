package dev.verifactu.aeat

/** The platform-managed mechanism that supplies an AEAT client certificate. */
public enum class AeatCredentialStrategy {
    /** A JVM platform adapter owns access to a key store or equivalent provider. */
    JVM_KEY_STORE,

    /** An Android platform adapter owns access to the Android credential provider. */
    ANDROID_KEY_CHAIN,

    /** An Apple platform adapter owns access to Keychain-backed credentials. */
    APPLE_KEYCHAIN,
}

/**
 * Opaque caller-defined alias for a certificate managed outside this library.
 *
 * The alias is not exposed or rendered, so diagnostics cannot accidentally reveal it.
 */
public class AeatCredentialReference private constructor(
    private val alias: String,
) {
    /** References compare by their caller-defined alias without exposing it. */
    override fun equals(other: Any?): Boolean = other is AeatCredentialReference && alias == other.alias

    /** Hashes the opaque alias for use in collections. */
    override fun hashCode(): Int = alias.hashCode()

    /** Redacts the opaque alias from diagnostic output. */
    override fun toString(): String = "AeatCredentialReference(redacted)"

    public companion object {
        /** Creates a reference from a non-empty alias managed by the host application. */
        public fun fromAlias(alias: String): AeatCredentialReference {
            require(alias.isNotBlank()) { "The credential alias must not be blank." }
            return AeatCredentialReference(alias)
        }
    }
}

/** Non-secret metadata describing a platform-managed certificate. */
public data class AeatCredentialMetadata(
    public val strategy: AeatCredentialStrategy,
    public val reference: AeatCredentialReference,
)

/**
 * Opaque platform credential contract for AEAT mutual-TLS transports.
 *
 * Implementations remain owned by the host application or platform adapter. This interface
 * intentionally contains no certificate bytes, private keys, passwords, or key-store paths.
 */
public interface AeatClientCredential {
    /** Sanitized metadata appropriate for typed diagnostics. */
    public val metadata: AeatCredentialMetadata
}
