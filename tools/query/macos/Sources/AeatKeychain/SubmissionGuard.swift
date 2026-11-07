import Foundation

let submissionNamespace = contractNamespace + "SuministroLR.xsd"

// JVM preparation rebuilds/XSD-validates the bundle; batches additionally require an exact explicit record count.
struct ReviewedSubmission: ReviewedRequest {
    let data: Data
    let sha256: String
    let description: String
    let buttonTitle: String

    init(data: Data, expectedDigest: String, expectedCount: Int = 1) throws {
        guard (1...1000).contains(expectedCount), data.count <= 8 * 1024 * 1024,
              let text = String(data: data, encoding: .utf8),
              !text.contains("<!DOCTYPE"), !text.contains("<!ENTITY"),
              sha256Hex(data) == expectedDigest else { throw QueryFailure.invalidInput }
        let document = try XMLDocument(data: data, options: [.nodeLoadExternalEntitiesNever])
        guard let envelope = document.rootElement(), envelope.localName == "Envelope", envelope.uri == soapNamespace,
              elements(envelope).count == 1,
              let body = elements(envelope).first, body.localName == "Body", body.uri == soapNamespace,
              elements(body).count == 1,
              let submission = elements(body).first,
              submission.localName == "RegFactuSistemaFacturacion", submission.uri == submissionNamespace else { throw QueryFailure.invalidInput }
        let entries = submission.elements(forLocalName: "RegistroFactura", uri: submissionNamespace)
        guard entries.count == expectedCount, elements(submission).count == expectedCount + 1 else { throw QueryFailure.invalidInput }
        let header = try only(submission, "Cabecera", submissionNamespace)
        let party = try only(header, "ObligadoEmision", commonNamespace)
        let nif = try only(party, "NIF", commonNamespace).stringValue ?? ""
        let name = try only(party, "NombreRazon", commonNamespace).stringValue ?? ""
        var numbers = Set<String>()
        var cancelling = false
        for entry in entries {
            guard elements(entry).count == 1, let record = elements(entry).first,
              record.uri == commonNamespace,
              ["RegistroAlta", "RegistroAnulacion"].contains(record.localName ?? "") else { throw QueryFailure.invalidInput }
            let identifier = try only(record, "IDFactura", commonNamespace)
            cancelling = record.localName == "RegistroAnulacion"
            let recordNif = try only(identifier, cancelling ? "IDEmisorFacturaAnulada" : "IDEmisorFactura", commonNamespace).stringValue ?? ""
            let number = try only(identifier, cancelling ? "NumSerieFacturaAnulada" : "NumSerieFactura", commonNamespace).stringValue ?? ""
            guard !nif.isEmpty, !name.isEmpty, nif == recordNif, !number.isEmpty,
                  numbers.insert(number).inserted, expectedCount == 1 || !cancelling else { throw QueryFailure.invalidInput }
        }
        self.data = data
        sha256 = expectedDigest
        buttonTitle = expectedCount == 1 ? "Send reviewed TEST record" : "Send reviewed TEST batch"
        description = "AEAT TEST WRITE · \(cancelling ? "Cancellation" : "Registration")\n" +
            "\(safeLabel(name)) · \(safeLabel(nif))\nRecords: \(expectedCount)\n" +
            "First invoice: \(safeLabel(numbers.sorted().first ?? ""))\nSHA-256: \(expectedDigest)"
    }
}
