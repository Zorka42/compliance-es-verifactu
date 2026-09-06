package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals

class AeatEnvironmentConfigurationTest {
    @Test
    fun resolvesOfficialTestAndProductionDefaults() {
        val test = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST)
        val production = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.PRODUCTION)

        assertEquals(
            "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
            test.url,
        )
        assertEquals(
            "https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
            production.url,
        )
        assertEquals(AeatServiceVersion.TIKE_V1_0, test.version)
        assertEquals(AeatEndpointConfiguration.OFFICIAL_WSDL_URL, test.sourceUrl)
        assertEquals(
            listOf(AeatSubmissionFeature.REGISTRATION_SUBMISSION, AeatSubmissionFeature.RECORD_QUERY),
            test.features,
        )
    }

    @Test
    fun selectsTheOfficialSelloCertificateEndpointsExplicitly() {
        val test =
            AeatEndpointConfiguration.defaultSubmissionEndpoint(
                environment = AeatEnvironment.TEST,
                certificateAccess = AeatCertificateAccess.SEAL,
            )
        val production =
            AeatEndpointConfiguration.defaultSubmissionEndpoint(
                environment = AeatEnvironment.PRODUCTION,
                certificateAccess = AeatCertificateAccess.SEAL,
            )

        assertEquals(
            "https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
            test.url,
        )
        assertEquals(
            "https://www10.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
            production.url,
        )
    }

    @Test
    fun appliesEndpointChangesOnlyThroughAdvancedConfiguration() {
        val endpoint =
            AeatEndpointConfiguration.submissionEndpointWithAdvancedOverride(
                environment = AeatEnvironment.TEST,
                advancedConfiguration =
                    AeatAdvancedEndpointConfiguration(
                        submissionUrl = "https://localhost:9443/verifactu",
                    ),
            )

        assertEquals("https://localhost:9443/verifactu", endpoint.url)
        assertEquals(AeatEnvironment.TEST, endpoint.environment)
        assertEquals(AeatCertificateAccess.STANDARD, endpoint.certificateAccess)
    }
}
