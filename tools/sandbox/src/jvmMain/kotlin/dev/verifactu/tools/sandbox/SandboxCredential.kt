package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatCredentialMetadata
import dev.verifactu.aeat.AeatCredentialReference
import dev.verifactu.aeat.AeatCredentialStrategy
import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.JvmAeatMtlsCredential
import dev.verifactu.aeat.JvmAeatMtlsTransport
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.PrivateKey
import java.time.Duration
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

internal fun loadSandboxTransport(
    path: Path,
    alias: String,
    password: CharArray,
): AeatTransportAdapter = JvmAeatMtlsTransport(loadCredential(path, alias, password), Duration.ofSeconds(30))

internal fun loadCredential(
    path: Path,
    alias: String,
    password: CharArray,
    trustManagers: Array<TrustManager>? = null,
): JvmAeatMtlsCredential {
    sandboxRequire(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Select the PKCS#12 file explicitly; links are not followed.")
    try {
        val source =
            KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(path).use { load(it, password) }
            }
        sandboxRequire(source.isKeyEntry(alias), "The explicitly selected alias is not a private-key identity.")
        val key = source.getKey(alias, password)
        val chain = source.getCertificateChain(alias)
        sandboxRequire(key is PrivateKey && !chain.isNullOrEmpty(), "The selected alias needs a private key and certificate chain.")
        // Filter to the explicitly selected identity: JSSE must not silently choose another alias.
        val selected =
            KeyStore.getInstance("PKCS12").apply {
                load(null, password)
                setKeyEntry("sandbox-selected", key, password, chain)
            }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(selected, password) }
        val context = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, trustManagers, null) }
        return JvmAeatMtlsCredential(
            AeatCredentialMetadata(AeatCredentialStrategy.JVM_KEY_STORE, AeatCredentialReference.fromAlias("sandbox-selected")),
            context,
        )
    } catch (_: GeneralSecurityException) {
        throw SandboxInputException("Cannot load the selected PKCS#12 identity; check the file, alias and password source.")
    } catch (_: java.io.IOException) {
        throw SandboxInputException("Cannot load the selected PKCS#12 identity; check the file, alias and password source.")
    }
}
