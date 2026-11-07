import Foundation

let submissionNamespace = contractNamespace + "SuministroLR.xsd"

// JVM preparation rebuilds/XSD-validates the bundle. This independent native gate permits one reviewed TEST record only.
struct ReviewedSubmission: ReviewedRequest {
    let data: Data
    let sha256: String
    let description: String
    let buttonTitle = "Send reviewed TEST record"

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
              let submission = elements(body).first,
              submission.localName == "RegFactuSistemaFacturacion", submission.uri == submissionNamespace,
              elements(submission).count == 2 else { throw QueryFailure.invalidInput }
        let header = try only(submission, "Cabecera", submissionNamespace)
        let party = try only(header, "ObligadoEmision", commonNamespace)
        let nif = try only(party, "NIF", commonNamespace).stringValue ?? ""
        let name = try only(party, "NombreRazon", commonNamespace).stringValue ?? ""
        let entry = try only(submission, "RegistroFactura", submissionNamespace)
        guard elements(entry).count == 1, let record = elements(entry).first,
              record.uri == commonNamespace,
              ["RegistroAlta", "RegistroAnulacion"].contains(record.localName ?? "") else { throw QueryFailure.invalidInput }
        let identifier = try only(record, "IDFactura", commonNamespace)
        let cancelling = record.localName == "RegistroAnulacion"
        let recordNif = try only(identifier, cancelling ? "IDEmisorFacturaAnulada" : "IDEmisorFactura", commonNamespace).stringValue ?? ""
        let number = try only(identifier, cancelling ? "NumSerieFacturaAnulada" : "NumSerieFactura", commonNamespace).stringValue ?? ""
        guard !nif.isEmpty, !name.isEmpty, nif == recordNif, !number.isEmpty else { throw QueryFailure.invalidInput }
        self.data = data
        sha256 = expectedDigest
        description = "AEAT TEST WRITE · \(cancelling ? "Cancellation" : "Registration")\n" +
            "\(safeLabel(name)) · \(safeLabel(nif))\nInvoice: \(safeLabel(number))\nSHA-256: \(expectedDigest)"
    }
}
