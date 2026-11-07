import Foundation
import Security

// Read the issuer RDN's common-name OID, never a localized label or the subject name.
func certificateIssuerCommonName(_ certificate: SecCertificate) -> String? {
    guard let values = SecCertificateCopyValues(certificate, [kSecOIDX509V1IssuerName] as CFArray, nil) as? [String: Any],
          let issuer = values[kSecOIDX509V1IssuerName as String] as? [String: Any],
          let attributes = issuer[kSecPropertyKeyValue as String] as? [[String: Any]] else { return nil }
    let names = attributes.filter { ($0[kSecPropertyKeyLabel as String] as? String) == (kSecOIDCommonName as String) }
    guard names.count == 1 else { return nil }
    return names[0][kSecPropertyKeyValue as String] as? String
}

func filterIdentities(_ identities: [SecIdentity], issuer: String?) -> [SecIdentity] {
    guard let issuer else { return identities }
    return identities.filter { identity in
        var certificate: SecCertificate?
        guard SecIdentityCopyCertificate(identity, &certificate) == errSecSuccess, let certificate else { return false }
        return certificateIssuerCommonName(certificate) == issuer
    }
}
