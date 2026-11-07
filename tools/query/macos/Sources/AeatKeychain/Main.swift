import Darwin
import Foundation
import Security

@main
struct KeychainMain {
    static func main() {
        do {
            try run(Array(CommandLine.arguments.dropFirst()))
        } catch {
            FileHandle.standardError.write(Data("Cannot complete the selected TEST request. No credentials or remote diagnostics are printed.\n".utf8))
            exit(2)
        }
    }

    static func run(_ arguments: [String], select: (any ReviewedRequest, String?, String?) throws -> SecIdentity = chooseIdentity) throws {
        let options = try parse(arguments)
        guard let endpoint = options["endpoint"], let request = options["request"], let digest = options["sha256"] else {
            throw QueryFailure.invalidInput
        }
        let target = try testEndpoint(endpoint)
        let data = try readRequest(request)
        let query: any ReviewedRequest
        if let confirmation = options["confirm-submission-sha256"] {
            guard confirmation == digest else { throw QueryFailure.invalidInput }
            query = try ReviewedSubmission(data: data, expectedDigest: confirmation)
        } else {
            query = try ReviewedQuery(data: data, expectedDigest: digest)
        }
        if options["validate-only"] != nil {
            print("Selected TEST request verified. No Keychain or network access.")
            return
        }
        guard let output = options["response"], let result = options["result"],
              !FileManager.default.fileExists(atPath: output), !FileManager.default.fileExists(atPath: result) else {
            throw QueryFailure.invalidInput
        }
        var fingerprint = "unavailable"
        var reply: QueryReply
        do {
            let identity = try select(query, options["identity-sha256"], options["issuer"])
            fingerprint = try identityDigest(identity)
            reply = QueryTransport(identity: identity, target: target).execute(query)
        } catch QueryFailure.selectionCancelled {
            reply = QueryReply(kind: "SELECTION_CANCELLED", delivery: "NOT_SENT", httpStatus: nil, body: nil)
        } catch {
            reply = QueryReply(kind: "IDENTITY_UNAVAILABLE", delivery: "NOT_SENT", httpStatus: nil, body: nil)
        }
        if let body = reply.body { try writeNew(body, at: output) }
        let receipt = "kind=\(reply.kind)\ndelivery=\(reply.delivery)\nhttp.status=\(reply.httpStatus.map(String.init) ?? "unavailable")\n" +
            "request.sha256=\(query.sha256)\nidentity.sha256=\(fingerprint)\n" +
            "request.url=\(target.absoluteString)\nresponse.url=\(reply.responseUrl ?? "unavailable")\n"
        try writeNew(Data(receipt.utf8), at: result)
    }

    static func parse(_ arguments: [String]) throws -> [String: String] {
        let allowed = ["endpoint", "request", "sha256", "response", "result", "identity-sha256", "issuer", "validate-only", "confirm-submission-sha256"]
        var options: [String: String] = [:]
        var index = 0
        while index < arguments.count {
            let option = arguments[index]
            guard option.hasPrefix("--") else { throw QueryFailure.invalidInput }
            let key = String(option.dropFirst(2))
            guard allowed.contains(key), options[key] == nil else { throw QueryFailure.invalidInput }
            index += 1
            if key == "validate-only" { options[key] = "true"; continue }
            guard index < arguments.count, !arguments[index].hasPrefix("--") else { throw QueryFailure.invalidInput }
            options[key] = arguments[index]
            index += 1
        }
        if let identity = options["identity-sha256"], identity.range(of: "^[a-f0-9]{64}$", options: .regularExpression) == nil {
            throw QueryFailure.invalidInput
        }
        if let issuer = options["issuer"] {
            guard !issuer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, issuer.utf8.count <= 1024,
                  !issuer.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else {
                throw QueryFailure.invalidInput
            }
        }
        return options
    }
}

private func readRequest(_ path: String) throws -> Data {
    let fd = open(path, O_RDONLY | O_NOFOLLOW)
    guard fd >= 0 else { throw QueryFailure.localFile }
    let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
    defer { try? handle.close() }
    var info = stat()
    guard fstat(fd, &info) == 0, info.st_mode & S_IFMT == S_IFREG else { throw QueryFailure.localFile }
    return try handle.read(upToCount: 65_537) ?? Data()
}

private func writeNew(_ data: Data, at path: String) throws {
    let fd = open(path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, S_IRUSR | S_IWUSR)
    guard fd >= 0 else { throw QueryFailure.localFile }
    let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
    defer { try? handle.close() }
    try handle.write(contentsOf: data)
}
