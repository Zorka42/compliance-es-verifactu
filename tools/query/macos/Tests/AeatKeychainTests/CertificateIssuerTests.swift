import Foundation
import Security
import XCTest
@testable import AeatKeychain

final class CertificateIssuerTests: XCTestCase {
    func testReadsIssuerCommonNameInsteadOfMisleadingSubjectAndHandlesMissingCommonName() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false,
                                                attributes: [.posixPermissions: 0o700])
        defer { try? FileManager.default.removeItem(at: root) }
        let config = root.appendingPathComponent("openssl.cnf")
        try "[req]\ndistinguished_name=dn\n[dn]\n".write(to: config, atomically: false, encoding: .utf8)
        func openssl(_ arguments: [String]) throws {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/openssl")
            process.arguments = arguments
            process.currentDirectoryURL = root
            process.standardOutput = FileHandle.nullDevice
            process.standardError = FileHandle.nullDevice
            try process.run()
            process.waitUntilExit()
            XCTAssertEqual(process.terminationStatus, 0)
            if process.terminationStatus != 0 { throw QueryFailure.invalidInput }
        }
        try openssl(["req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-config", config.path,
                     "-subj", "/CN=Synthetic test CA", "-keyout", "ca.key", "-out", "ca.pem"])
        try openssl(["req", "-new", "-newkey", "rsa:2048", "-nodes", "-config", config.path,
                     "-subj", "/CN=AC FNMT Usuarios", "-keyout", "leaf.key", "-out", "leaf.csr"])
        try openssl(["x509", "-req", "-in", "leaf.csr", "-CA", "ca.pem", "-CAkey", "ca.key",
                     "-set_serial", "1", "-days", "1", "-outform", "DER", "-out", "leaf.der"])
        let data = try Data(contentsOf: root.appendingPathComponent("leaf.der"))
        let certificate = try XCTUnwrap(SecCertificateCreateWithData(nil, data as CFData))
        XCTAssertEqual(certificateIssuerCommonName(certificate), "Synthetic test CA")
        XCTAssertNotEqual(certificateIssuerCommonName(certificate), "AC FNMT Usuarios")
        try openssl(["req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-config", config.path,
                     "-subj", "/O=Synthetic issuer without CN", "-keyout", "unnamed.key", "-outform", "DER", "-out", "unnamed.der"])
        let unnamedData = try Data(contentsOf: root.appendingPathComponent("unnamed.der"))
        let unnamed = try XCTUnwrap(SecCertificateCreateWithData(nil, unnamedData as CFData))
        XCTAssertNil(certificateIssuerCommonName(unnamed))
    }
}
