import AppKit
import Security
import SecurityInterface

func identityDigest(_ identity: SecIdentity) throws -> String {
    var certificate: SecCertificate?
    guard SecIdentityCopyCertificate(identity, &certificate) == errSecSuccess, let certificate else {
        throw QueryFailure.identityUnavailable
    }
    return sha256Hex(SecCertificateCopyData(certificate) as Data)
}

func chooseIdentity(for query: ReviewedQuery, previousDigest: String?) throws -> SecIdentity {
    var found: CFTypeRef?
    let search: [String: Any] = [
        kSecClass as String: kSecClassIdentity,
        kSecReturnRef as String: true,
        kSecMatchLimit as String: kSecMatchLimitAll,
    ]
    let status = SecItemCopyMatching(search as CFDictionary, &found)
    guard status == errSecSuccess, let identities = found as? [SecIdentity], !identities.isEmpty else {
        throw QueryFailure.identityUnavailable
    }
    if let previousDigest {
        let matches = identities.filter { (try? identityDigest($0)) == previousDigest }
        guard matches.count == 1, let identity = matches.first else { throw QueryFailure.identityUnavailable }
        return identity
    }
    let application = NSApplication.shared
    application.setActivationPolicy(.accessory)
    application.activate(ignoringOtherApps: true)
    guard let panel = SFChooseIdentityPanel.shared() else { throw QueryFailure.identityUnavailable }
    panel.setPolicies(SecPolicyCreateSSL(false, nil))
    panel.setDefaultButtonTitle("Use for TEST consultation")
    panel.setAlternateButtonTitle("Cancel")
    // Setting a domain would persist an identity preference in Keychain. Selection stays session-local.
    let answer = panel.runModal(forIdentities: identities, message: query.description)
    guard answer == NSApplication.ModalResponse.OK.rawValue, let identity = panel.identity() else {
        throw QueryFailure.selectionCancelled
    }
    return identity.takeUnretainedValue()
}
