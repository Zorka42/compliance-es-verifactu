package dev.verifactu.aeat

/** The AEAT environment selected by the integrating application. */
public enum class AeatEnvironment {
    /** The AEAT external testing environment. */
    TEST,

    /** The AEAT production environment. */
    PRODUCTION,
}

/** The certificate access route identified in the AEAT VeriFactu WSDL. */
public enum class AeatCertificateAccess {
    /** The standard client-certificate route. */
    STANDARD,

    /** The route explicitly published for a certificate of seal. */
    SEAL,
}

/** The pinned AEAT VeriFactu service version. */
public enum class AeatServiceVersion {
    /** The `tikeV1.0` WSDL published by AEAT. */
    TIKE_V1_0,
}

/** Operations advertised by the pinned VeriFactu WSDL. */
public enum class AeatSubmissionFeature {
    /** Send fiscal-record batches through `RegFactuSistemaFacturacion`. */
    REGISTRATION_SUBMISSION,

    /** Query submitted fiscal records through `ConsultaFactuSistemaFacturacion`. */
    RECORD_QUERY,
}

/**
 * Resolved AEAT endpoint metadata.
 *
 * The endpoint is data only; resolving it performs neither I/O nor certificate access.
 */
public data class AeatSubmissionEndpoint(
    public val environment: AeatEnvironment,
    public val certificateAccess: AeatCertificateAccess,
    public val version: AeatServiceVersion,
    public val url: String,
    public val sourceUrl: String,
    public val features: List<AeatSubmissionFeature>,
)

/**
 * Explicit opt-in configuration for a non-default endpoint.
 *
 * This is intended for controlled integration testing or a future AEAT endpoint migration.
 * Callers are responsible for validating the endpoint against the applicable AEAT publication.
 */
public data class AeatAdvancedEndpointConfiguration(
    public val submissionUrl: String,
)

/**
 * Resolves the pinned AEAT VeriFactu endpoint set.
 *
 * Source: `SistemaFacturacion.wsdl`, `tikeV1.0`, retrieved on 2026-08-16.
 */
public object AeatEndpointConfiguration {
    /** Official WSDL that publishes the version, operations, and endpoint addresses. */
    public const val OFFICIAL_WSDL_URL: String =
        "https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SistemaFacturacion.wsdl"

    /** Resolves the AEAT-published default endpoint for an environment and certificate route. */
    public fun defaultSubmissionEndpoint(
        environment: AeatEnvironment,
        certificateAccess: AeatCertificateAccess = AeatCertificateAccess.STANDARD,
    ): AeatSubmissionEndpoint =
        AeatSubmissionEndpoint(
            environment = environment,
            certificateAccess = certificateAccess,
            version = AeatServiceVersion.TIKE_V1_0,
            url = defaultUrl(environment, certificateAccess),
            sourceUrl = OFFICIAL_WSDL_URL,
            features = listOf(AeatSubmissionFeature.REGISTRATION_SUBMISSION, AeatSubmissionFeature.RECORD_QUERY),
        )

    /**
     * Resolves an endpoint while explicitly recording an advanced override.
     *
     * Normal consumers should use [defaultSubmissionEndpoint].
     */
    public fun submissionEndpointWithAdvancedOverride(
        environment: AeatEnvironment,
        certificateAccess: AeatCertificateAccess = AeatCertificateAccess.STANDARD,
        advancedConfiguration: AeatAdvancedEndpointConfiguration,
    ): AeatSubmissionEndpoint {
        require(advancedConfiguration.submissionUrl.startsWith("https://")) {
            "The advanced AEAT submission endpoint must use HTTPS."
        }
        return defaultSubmissionEndpoint(environment, certificateAccess).copy(
            url = advancedConfiguration.submissionUrl,
        )
    }
}

private fun defaultUrl(
    environment: AeatEnvironment,
    certificateAccess: AeatCertificateAccess,
): String =
    when (environment) {
        AeatEnvironment.TEST ->
            when (certificateAccess) {
                AeatCertificateAccess.STANDARD ->
                    "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
                AeatCertificateAccess.SEAL ->
                    "https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
            }
        AeatEnvironment.PRODUCTION ->
            when (certificateAccess) {
                AeatCertificateAccess.STANDARD ->
                    "https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
                AeatCertificateAccess.SEAL ->
                    "https://www10.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
            }
    }
