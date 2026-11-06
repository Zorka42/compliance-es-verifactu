package dev.verifactu.tools.sandbox

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

internal const val DISPOSABLE_PASSWORD: String = "local-disposable-tests-only"

internal class DisposableSandboxTls(
    root: Path,
) : AutoCloseable {
    val clientFile: Path = root.resolve("selected.p12")
    val server: HttpsServer = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val clientTrust: Array<TrustManager>
    var handler: (HttpsExchange) -> Unit = { error("Configure the local handler") }

    init {
        val serverFile = root.resolve("server.p12")
        val serverTrust = root.resolve("server-trust.p12")
        val clientTrustFile = root.resolve("client-trust.p12")
        generate(serverFile, "server", "localhost", "serverAuth", "SAN=dns:localhost,ip:127.0.0.1")
        generate(clientFile, "selected-client", "selected-client", "clientAuth")
        generate(clientFile, "other-client", "other-client", "clientAuth")
        trust(serverFile, "server", root.resolve("server.cer"), clientTrustFile)
        trust(clientFile, "selected-client", root.resolve("client.cer"), serverTrust)
        clientTrust = trustManagers(clientTrustFile)
        val keys =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store(serverFile), DISPOSABLE_PASSWORD.toCharArray())
            }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trustManagers(serverTrust), null) }
        server.httpsConfigurator =
            object : HttpsConfigurator(context) {
                override fun configure(parameters: HttpsParameters) {
                    parameters.setSSLParameters(context.defaultSSLParameters.apply { needClientAuth = true })
                }
            }
        server.createContext("/") { handler(it as HttpsExchange) }
    }

    fun start() {
        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    private fun generate(
        file: Path,
        alias: String,
        cn: String,
        usage: String,
        vararg extensions: String,
    ) {
        keytool(
            "-genkeypair",
            "-alias",
            alias,
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "1",
            "-dname",
            "CN=$cn",
            "-ext",
            "EKU=$usage",
            *extensions.flatMap { listOf("-ext", it) }.toTypedArray(),
            "-storetype",
            "PKCS12",
            "-keystore",
            file.toString(),
            "-storepass",
            DISPOSABLE_PASSWORD,
            "-keypass",
            DISPOSABLE_PASSWORD,
            "-noprompt",
        )
    }

    private fun trust(
        source: Path,
        alias: String,
        certificate: Path,
        target: Path,
    ) {
        keytool(
            "-exportcert",
            "-alias",
            alias,
            "-keystore",
            source.toString(),
            "-storepass",
            DISPOSABLE_PASSWORD,
            "-file",
            certificate.toString(),
        )
        keytool(
            "-importcert",
            "-alias",
            alias,
            "-file",
            certificate.toString(),
            "-storetype",
            "PKCS12",
            "-keystore",
            target.toString(),
            "-storepass",
            DISPOSABLE_PASSWORD,
            "-noprompt",
        )
    }

    private fun store(path: Path): KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(path).use { load(it, DISPOSABLE_PASSWORD.toCharArray()) }
        }

    private fun trustManagers(path: Path): Array<TrustManager> =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply {
                init(store(path))
            }.trustManagers

    private fun keytool(vararg args: String) {
        val process =
            ProcessBuilder(
                listOf(Path.of(System.getProperty("java.home"), "bin", "keytool").toString()) + args,
            ).redirectErrorStream(true).start()
        process.inputStream.use { it.readBytes() }
        check(process.waitFor() == 0) { "Disposable TLS test identity generation failed." }
    }
}
