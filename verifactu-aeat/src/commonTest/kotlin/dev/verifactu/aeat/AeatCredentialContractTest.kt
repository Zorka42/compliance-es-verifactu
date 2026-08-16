package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AeatCredentialContractTest {
    @Test
    fun platformCredentialsExposeOnlySafeMetadataToCommonCode() {
        val credential =
            TestCredential(
                AeatCredentialMetadata(
                    strategy = AeatCredentialStrategy.JVM_KEY_STORE,
                    reference = AeatCredentialReference.fromAlias("integration-test-certificate"),
                ),
            )

        assertEquals(AeatCredentialStrategy.JVM_KEY_STORE, credential.metadata.strategy)
        assertEquals(
            AeatCredentialReference.fromAlias("integration-test-certificate"),
            credential.metadata.reference,
        )
    }

    @Test
    fun credentialReferencesNeverRenderTheirAlias() {
        val reference = AeatCredentialReference.fromAlias("not-a-secret-but-still-private")

        assertFalse(reference.toString().contains("not-a-secret-but-still-private"))
    }

    @Test
    fun credentialReferenceRejectsBlankAliases() {
        assertFailsWith<IllegalArgumentException> {
            AeatCredentialReference.fromAlias("   ")
        }
    }

    private class TestCredential(
        override val metadata: AeatCredentialMetadata,
    ) : AeatClientCredential
}
