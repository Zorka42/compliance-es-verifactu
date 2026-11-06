import Foundation
import XCTest
@testable import AeatKeychain

let fixtureQuery = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>
<con:ConsultaFactuSistemaFacturacion xmlns:con="\(queryNamespace)" xmlns:sf="\(commonNamespace)">
<con:Cabecera><sf:IDVersion>1.0</sf:IDVersion><sf:ObligadoEmision>
<sf:NombreRazon>Synthetic taxpayer</sf:NombreRazon><sf:NIF>89890001K</sf:NIF>
</sf:ObligadoEmision></con:Cabecera><con:FiltroConsulta><con:PeriodoImputacion>
<sf:Ejercicio>2026</sf:Ejercicio><sf:Periodo>11</sf:Periodo>
</con:PeriodoImputacion></con:FiltroConsulta></con:ConsultaFactuSistemaFacturacion></soap:Body></soap:Envelope>
"""

final class QueryGuardTests: XCTestCase {
    func testAcceptsOnlyReviewedConsultationAndExactTestRoutes() throws {
        let data = Data(fixtureQuery.utf8)
        let query = try ReviewedQuery(data: data, expectedDigest: sha256Hex(data))
        XCTAssertEqual(query.data, data)
        XCTAssertTrue(query.description.contains("TEST consultation only"))
        XCTAssertTrue(query.description.contains("2026-11"))
        XCTAssertThrowsError(try ReviewedQuery(data: data, expectedDigest: String(repeating: "0", count: 64)))
        XCTAssertEqual(try testEndpoint("https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP").host, "prewww1.aeat.es")
        XCTAssertEqual(try testEndpoint("https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP").host, "prewww10.aeat.es")
        for target in ["https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP", "http://prewww1.aeat.es/", "https://localhost/", "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP?x=1"] {
            XCTAssertThrowsError(try testEndpoint(target))
        }
    }

    func testRejectsSubmissionUnsafeXmlAndAmbiguousBodiesBeforeKeychain() {
        let values = [
            fixtureQuery.replacingOccurrences(of: "ConsultaFactuSistemaFacturacion", with: "RegFactuSistemaFacturacion"),
            fixtureQuery.replacingOccurrences(of: "<soap:Body>", with: "<soap:Body/><soap:Body>"),
            "<!DOCTYPE x SYSTEM 'https://example.invalid/never-fetch'>" + fixtureQuery,
            fixtureQuery.replacingOccurrences(of: queryNamespace, with: "https://example.invalid/wrong"),
            fixtureQuery.replacingOccurrences(of: "<sf:Periodo>11</sf:Periodo>", with: "<sf:Periodo>13</sf:Periodo>"),
            fixtureQuery.replacingOccurrences(of: "</sf:ObligadoEmision>", with: "</sf:ObligadoEmision><sf:Destinatario/>"),
        ]
        for text in values {
            let data = Data(text.utf8)
            XCTAssertThrowsError(try ReviewedQuery(data: data, expectedDigest: sha256Hex(data)))
        }
        XCTAssertThrowsError(try ReviewedQuery(data: Data(repeating: 65, count: 65_537), expectedDigest: "invalid"))
        XCTAssertThrowsError(try KeychainMain.parse(["--password", "PRIVATE"]))
        XCTAssertThrowsError(try KeychainMain.parse(["--request", "a", "--request", "b"]))
        XCTAssertThrowsError(try KeychainMain.parse(["--request"]))
        XCTAssertThrowsError(try KeychainMain.parse(["--identity-sha256", "bad"]))
    }
}
