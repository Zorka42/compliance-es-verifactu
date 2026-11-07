import Foundation
import XCTest
@testable import AeatKeychain

// This fixture exercises the native gate only. Full fiscal/XSD validation belongs to JVM bundle preparation.
func submissionFixture(cancellation: Bool = false) -> String {
    let operation = cancellation ? "RegistroAnulacion" : "RegistroAlta"
    let suffix = cancellation ? "Anulada" : ""
    return """
    <soap:Envelope xmlns:soap="\(soapNamespace)"><soap:Body>
    <lr:RegFactuSistemaFacturacion xmlns:lr="\(submissionNamespace)" xmlns:sf="\(commonNamespace)">
    <lr:Cabecera><sf:ObligadoEmision><sf:NombreRazon>Synthetic taxpayer</sf:NombreRazon>
    <sf:NIF>89890001K</sf:NIF></sf:ObligadoEmision></lr:Cabecera>
    <lr:RegistroFactura><sf:\(operation)><sf:IDFactura>
    <sf:IDEmisorFactura\(suffix)>89890001K</sf:IDEmisorFactura\(suffix)>
    <sf:NumSerieFactura\(suffix)>TEST-ONLY-1</sf:NumSerieFactura\(suffix)>
    </sf:IDFactura></sf:\(operation)></lr:RegistroFactura>
    </lr:RegFactuSistemaFacturacion></soap:Body></soap:Envelope>
    """
}

final class SubmissionGuardTests: XCTestCase {
    func testAllowsOneMatchingRegistrationOrCancellationWithExplicitWriteIntent() throws {
        for cancellation in [false, true] {
            let data = Data(submissionFixture(cancellation: cancellation).utf8)
            let request = try ReviewedSubmission(data: data, expectedDigest: sha256Hex(data))
            XCTAssertEqual(request.data, data)
            XCTAssertTrue(request.description.contains("TEST WRITE"))
            XCTAssertTrue(request.description.contains(cancellation ? "Cancellation" : "Registration"))
            XCTAssertEqual(request.buttonTitle, "Send reviewed TEST record")
            XCTAssertThrowsError(try ReviewedQuery(data: data, expectedDigest: sha256Hex(data)))
            XCTAssertThrowsError(try ReviewedSubmission(data: data, expectedDigest: String(repeating: "0", count: 64)))
        }
    }

    func testRejectsQueriesMultipleRecordsUnsafeXmlWrongOperationAndDifferentIssuer() {
        let xml = submissionFixture()
        let invalid = [
            fixtureQuery,
            xml.replacingOccurrences(of: "<soap:Body>", with: "<soap:Body/><soap:Body>"),
            xml.replacingOccurrences(of: "<lr:RegistroFactura>", with: "<lr:RegistroFactura/><lr:RegistroFactura>"),
            xml.replacingOccurrences(of: "RegistroAlta", with: "OtherOperation"),
            xml.replacingOccurrences(of: "<sf:IDEmisorFactura>89890001K", with: "<sf:IDEmisorFactura>89890002Q"),
            xml.replacingOccurrences(of: submissionNamespace, with: "https://example.invalid/wrong"),
            "<!DOCTYPE x SYSTEM 'https://example.invalid/never-fetch'>" + xml,
        ]
        for text in invalid {
            let data = Data(text.utf8)
            XCTAssertThrowsError(try ReviewedSubmission(data: data, expectedDigest: sha256Hex(data)))
        }
    }

    func testMainRequiresSeparateDigestAndTestRouteBeforeSelectionAndCancellationSendsNothing() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: root) }
        let data = Data(submissionFixture().utf8)
        let request = root.appendingPathComponent("request.xml")
        try data.write(to: request)
        let digest = sha256Hex(data)
        let arguments = ["--endpoint", "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                         "--request", request.path, "--sha256", digest,
                         "--response", root.appendingPathComponent("response.xml").path,
                         "--result", root.appendingPathComponent("result.properties").path]
        XCTAssertThrowsError(try KeychainMain.run(arguments) { _, _, _ in XCTFail("Read path must reject writes"); throw QueryFailure.invalidInput })
        let confirmed = arguments + ["--confirm-submission-sha256", digest]
        let production = confirmed.map { $0.replacingOccurrences(of: "prewww1.aeat.es", with: "www1.agenciatributaria.gob.es") }
        XCTAssertThrowsError(try KeychainMain.run(production) { _, _, _ in XCTFail("No production identity access"); throw QueryFailure.invalidInput })
        XCTAssertThrowsError(try KeychainMain.run(arguments + ["--confirm-submission-sha256", "wrong"]) { _, _, _ in XCTFail("No unreviewed identity access"); throw QueryFailure.invalidInput })
        try KeychainMain.run(confirmed + ["--validate-only"]) { _, _, _ in XCTFail("Validation is offline"); throw QueryFailure.invalidInput }
        try KeychainMain.run(confirmed) { reviewed, _, _ in
            XCTAssertTrue(reviewed is ReviewedSubmission)
            throw QueryFailure.selectionCancelled
        }
        let result = try String(contentsOf: root.appendingPathComponent("result.properties"), encoding: .utf8)
        XCTAssertTrue(result.contains("delivery=NOT_SENT"))
        XCTAssertTrue(result.contains("response.url=unavailable"))
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("response.xml").path))
    }
}
