import CryptoKit
import Foundation

enum QueryFailure: Error {
    case invalidInput, selectionCancelled, identityUnavailable, localFile
}

let soapNamespace = "http://schemas.xmlsoap.org/soap/envelope/"
let contractNamespace = "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/"
let queryNamespace = contractNamespace + "ConsultaLR.xsd"
let commonNamespace = contractNamespace + "SuministroInformacion.xsd"

protocol ReviewedRequest {
    var data: Data { get }
    var sha256: String { get }
    var description: String { get }
    var buttonTitle: String { get }
}

struct ReviewedQuery: ReviewedRequest {
    let buttonTitle = "Use for TEST consultation"
    let data: Data
    let sha256: String
    let description: String

    init(data: Data, expectedDigest: String) throws {
        guard data.count <= 65_536,
              let text = String(data: data, encoding: .utf8),
              !text.contains("<!DOCTYPE"), !text.contains("<!ENTITY"),
              sha256Hex(data) == expectedDigest else { throw QueryFailure.invalidInput }
        let document = try XMLDocument(data: data, options: [.nodeLoadExternalEntitiesNever])
        guard let envelope = document.rootElement(), envelope.localName == "Envelope", envelope.uri == soapNamespace,
              elements(envelope).count == 1,
              let body = elements(envelope).first, body.localName == "Body", body.uri == soapNamespace,
              elements(body).count == 1,
              let query = elements(body).first, query.localName == "ConsultaFactuSistemaFacturacion", query.uri == queryNamespace
        else { throw QueryFailure.invalidInput }
        let header = try only(query, "Cabecera", queryNamespace)
        let parties = header.elements(forLocalName: "ObligadoEmision", uri: commonNamespace) +
            header.elements(forLocalName: "Destinatario", uri: commonNamespace)
        guard parties.count == 1, let party = parties.first else { throw QueryFailure.invalidInput }
        let nif = try only(party, "NIF", commonNamespace).stringValue ?? ""
        let name = try only(party, "NombreRazon", commonNamespace).stringValue ?? ""
        let filter = try only(query, "FiltroConsulta", queryNamespace)
        let period = try only(filter, "PeriodoImputacion", queryNamespace)
        let year = try only(period, "Ejercicio", commonNamespace).stringValue ?? ""
        let month = try only(period, "Periodo", commonNamespace).stringValue ?? ""
        guard !nif.isEmpty, !name.isEmpty,
              year.range(of: "^[0-9]{4}$", options: .regularExpression) != nil,
              month.range(of: "^(0[1-9]|1[0-2])$", options: .regularExpression) != nil
        else { throw QueryFailure.invalidInput }
        self.data = data
        sha256 = expectedDigest
        description = "AEAT TEST consultation only · \(year)-\(month)\n\(safeLabel(name)) · \(safeLabel(nif))\n" +
            (party.localName == "ObligadoEmision" ? "Issued invoices" : "Received invoices")
    }
}

func testEndpoint(_ value: String) throws -> URL {
    let allowed = ["https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                   "https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"]
    guard allowed.contains(value), let url = URL(string: value) else { throw QueryFailure.invalidInput }
    return url
}

func sha256Hex(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
}

func elements(_ node: XMLElement) -> [XMLElement] {
    (node.children ?? []).compactMap { $0 as? XMLElement }
}

func only(_ node: XMLElement, _ name: String, _ namespace: String) throws -> XMLElement {
    let matches = node.elements(forLocalName: name, uri: namespace)
    guard matches.count == 1, let match = matches.first else { throw QueryFailure.invalidInput }
    return match
}

func safeLabel(_ text: String) -> String {
    String(text.unicodeScalars.filter {
        !CharacterSet.controlCharacters.contains($0) && !(0x202A...0x202E).contains($0.value) && !(0x2066...0x2069).contains($0.value)
    }.prefix(160))
}
