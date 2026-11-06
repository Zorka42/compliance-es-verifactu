import Foundation
import Network
import Security
import XCTest
@testable import AeatKeychain

final class NativeTransportTests: XCTestCase {
    func testCancellationDoesNotStartTransportOrWriteResponse() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: root) }
        let data = Data(fixtureQuery.utf8)
        let request = root.appendingPathComponent("request.xml")
        try data.write(to: request)
        let result = root.appendingPathComponent("result.properties")
        let response = root.appendingPathComponent("response.xml")
        let arguments = ["--endpoint", "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                         "--request", request.path, "--sha256", sha256Hex(data), "--response", response.path, "--result", result.path]
        try KeychainMain.run(arguments) { _, _ in throw QueryFailure.selectionCancelled }
        let receipt = try String(contentsOf: result, encoding: .utf8)
        XCTAssertTrue(receipt.contains("kind=SELECTION_CANCELLED"))
        XCTAssertTrue(receipt.contains("delivery=NOT_SENT"))
        XCTAssertFalse(FileManager.default.fileExists(atPath: response.path))
        XCTAssertThrowsError(try KeychainMain.run(arguments) { _, _ in XCTFail("No second selection"); throw QueryFailure.invalidInput })
        let permissions = try FileManager.default.attributesOfItem(atPath: result.path)[.posixPermissions] as? NSNumber
        XCTAssertEqual(permissions?.intValue, 0o600)
        let invalid = arguments.map { $0.replacingOccurrences(of: "prewww1.aeat.es", with: "www1.agenciatributaria.gob.es") }
        XCTAssertThrowsError(try KeychainMain.run(invalid) { _, _ in XCTFail("No identity access before TEST routing"); throw QueryFailure.invalidInput })
    }

    func testNativeMutualTlsUsesMemoryOnlyIdentityAndExactQuery() throws {
        guard #available(macOS 15, *) else { throw XCTSkip("Memory-only PKCS#12 import requires macOS 15.") }
        let identities = try DisposableIdentities()
        defer { identities.remove() }
        let data = Data(fixtureQuery.utf8)
        let query = try ReviewedQuery(data: data, expectedDigest: sha256Hex(data))
        let response = Data("<synthetic-response/>".utf8)
        let server = try LocalTlsServer(identity: identities.server, clientAnchor: identities.clientCertificate,
                                        response: response, expected: data)
        defer { server.close() }
        let reply = QueryTransport(identity: identities.client, target: server.url, anchor: identities.serverCertificate).execute(query)
        XCTAssertEqual(reply.kind, "XML_RESPONSE")
        XCTAssertEqual(reply.delivery, "RESPONSE_RECEIVED")
        XCTAssertEqual(reply.httpStatus, 200)
        XCTAssertEqual(reply.body, response)
        XCTAssertEqual(server.requests, 1)
        XCTAssertTrue(server.requestMatched)
        XCTAssertEqual(try identityDigest(identities.client).count, 64)
        let limited = QueryTransport(identity: identities.client, target: server.url, limit: 4, anchor: identities.serverCertificate).execute(query)
        XCTAssertEqual(limited.kind, "RESPONSE_TOO_LARGE")
        XCTAssertNil(limited.body)
        let untrusted = QueryTransport(identity: identities.client, target: server.url).execute(query)
        XCTAssertEqual(untrusted.kind, "UNKNOWN_DELIVERY")
        XCTAssertNil(untrusted.body)
        let redirect = try LocalTlsServer(identity: identities.server, clientAnchor: identities.clientCertificate,
                                          response: response, expected: data,
                                          headers: "HTTP/1.1 302 Found\r\nLocation: https://localhost:9/never-follow\r\nContent-Type: text/xml")
        defer { redirect.close() }
        let redirected = QueryTransport(identity: identities.client, target: redirect.url, anchor: identities.serverCertificate).execute(query)
        XCTAssertEqual(redirected.httpStatus, 302)
        XCTAssertEqual(redirected.kind, "XML_RESPONSE")
        XCTAssertEqual(redirect.requests, 1)
        let html = try LocalTlsServer(identity: identities.server, clientAnchor: identities.clientCertificate,
                                      response: Data("<html>not SOAP</html>".utf8), expected: data,
                                      headers: "HTTP/1.1 200 OK\r\nContent-Type: text/html")
        defer { html.close() }
        let nonXml = QueryTransport(identity: identities.client, target: html.url, anchor: identities.serverCertificate).execute(query)
        XCTAssertEqual(nonXml.kind, "NON_XML_RESPONSE")
        XCTAssertNil(nonXml.body)

    }
}

@available(macOS 15, *)
private final class DisposableIdentities {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    let server: SecIdentity
    let client: SecIdentity
    let serverCertificate: SecCertificate
    let clientCertificate: SecCertificate

    init() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false,
                                                attributes: [.posixPermissions: 0o700])
        do {
            let config = root.appendingPathComponent("openssl.cnf")
            try """
            [req]
            distinguished_name=dn
            x509_extensions=extensions
            [dn]
            [extensions]
            subjectAltName=DNS:localhost,IP:127.0.0.1
            basicConstraints=critical,CA:TRUE
            keyUsage=digitalSignature,keyEncipherment,keyCertSign
            extendedKeyUsage=serverAuth,clientAuth
            """.write(to: config, atomically: false, encoding: .utf8)
            let directory = root
            func generate(_ name: String) throws -> SecIdentity {
                let key = directory.appendingPathComponent(name + ".key").path
                let certificate = directory.appendingPathComponent(name + ".pem").path
                let p12 = directory.appendingPathComponent(name + ".p12")
                try openssl(["req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=localhost",
                             "-config", config.path, "-keyout", key, "-out", certificate])
                try openssl(["pkcs12", "-export", "-inkey", key, "-in", certificate, "-out", p12.path,
                             "-keypbe", "PBE-SHA1-3DES", "-certpbe", "PBE-SHA1-3DES", "-macalg", "sha1",
                             "-passout", "pass:disposable-test-only"])
                var imported: CFArray?
                let options = [kSecImportExportPassphrase as String: "disposable-test-only", kSecImportToMemoryOnly as String: true] as [String: Any]
                let status = SecPKCS12Import(try Data(contentsOf: p12) as CFData, options as CFDictionary, &imported)
                guard status == errSecSuccess, let entries = imported as? [[String: Any]],
                      let entry = entries.first, let identity = entry[kSecImportItemIdentity as String] else {
                    throw QueryFailure.identityUnavailable
                }
                return identity as! SecIdentity
            }
            server = try generate("server")
            client = try generate("client")
            func certificate(_ identity: SecIdentity) throws -> SecCertificate {
                var value: SecCertificate?
                guard SecIdentityCopyCertificate(identity, &value) == errSecSuccess, let value else { throw QueryFailure.identityUnavailable }
                return value
            }
            serverCertificate = try certificate(server)
            clientCertificate = try certificate(client)
        } catch {
            try? FileManager.default.removeItem(at: root)
            throw error
        }
    }

    func remove() { try? FileManager.default.removeItem(at: root) }
}

private func openssl(_ arguments: [String]) throws {
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/openssl")
    process.arguments = arguments
    process.standardOutput = FileHandle.nullDevice
    process.standardError = FileHandle.nullDevice
    try process.run()
    process.waitUntilExit()
    guard process.terminationStatus == 0 else { throw QueryFailure.invalidInput }
}

private final class LocalTlsServer {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "disposable-query-server")
    private let lock = NSLock()
    private var count = 0
    private var matched = true
    var url: URL { URL(string: "https://localhost:\(listener.port!.rawValue)/query")! }
    var requests: Int { lock.lock(); defer { lock.unlock() }; return count }
    var requestMatched: Bool { lock.lock(); defer { lock.unlock() }; return matched }

    init(identity: SecIdentity, clientAnchor: SecCertificate, response: Data, expected: Data,
         headers: String = "HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=UTF-8") throws {
        let tls = NWProtocolTLS.Options()
        sec_protocol_options_set_local_identity(tls.securityProtocolOptions, sec_identity_create(identity)!)
        sec_protocol_options_set_peer_authentication_required(tls.securityProtocolOptions, true)
        sec_protocol_options_set_verify_block(tls.securityProtocolOptions, { _, trust, done in
            let value = sec_trust_copy_ref(trust).takeRetainedValue()
            SecTrustSetAnchorCertificates(value, [clientAnchor] as CFArray)
            SecTrustSetAnchorCertificatesOnly(value, true)
            done(SecTrustEvaluateWithError(value, nil))
        }, queue)
        let parameters = NWParameters(tls: tls, tcp: NWProtocolTCP.Options())
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        listener = try NWListener(using: parameters)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
        listener.newConnectionHandler = { [weak self] connection in
            connection.start(queue: self?.queue ?? DispatchQueue.global())
            self?.receive(connection, data: Data(), response: response, expected: expected, headers: headers)
        }
        listener.start(queue: queue)
        guard ready.wait(timeout: .now() + 5) == .success, listener.port != nil else {
            listener.cancel()
            throw QueryFailure.invalidInput
        }
    }

    private func receive(_ connection: NWConnection, data: Data, response: Data, expected: Data, headers: String) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] part, _, complete, error in
            guard let self, error == nil, let part else { connection.cancel(); return }
            var collected = data
            collected.append(part)
            if let boundary = collected.range(of: Data("\r\n\r\n".utf8)), collected.count >= boundary.upperBound + expected.count {
                let header = String(data: collected[..<boundary.lowerBound], encoding: .utf8) ?? ""
                self.lock.lock()
                self.count += 1
                self.matched = self.matched && header.hasPrefix("POST /query HTTP/1.1") &&
                    Data(collected[boundary.upperBound...]) == expected && header.lowercased().contains("soapaction: \"\"")
                self.lock.unlock()
                var output = Data("\(headers)\r\nContent-Length: \(response.count)\r\nConnection: close\r\n\r\n".utf8)
                output.append(response)
                connection.send(content: output, completion: .contentProcessed { _ in connection.cancel() })
            } else if !complete && collected.count < 65_536 {
                self.receive(connection, data: collected, response: response, expected: expected, headers: headers)
            } else { connection.cancel() }
        }
    }

    func close() { listener.cancel() }
}
