import Foundation
import Security

struct QueryReply {
    let kind: String
    let delivery: String
    let httpStatus: Int?
    let body: Data?
    var responseUrl: String? = nil
}

final class QueryTransport: NSObject, URLSessionDataDelegate {
    private let identity: SecIdentity
    private let target: URL
    private let limit: Int
    private let anchor: SecCertificate?
    private let completed = DispatchSemaphore(value: 0)
    private var body = Data()
    private var response: HTTPURLResponse?
    private var tooLarge = false
    private var failed = false

    // The optional trust anchor is for disposable loopback tests. The executable never supplies one.
    init(identity: SecIdentity, target: URL, limit: Int = 64 * 1024 * 1024, anchor: SecCertificate? = nil) {
        self.identity = identity
        self.target = target
        self.limit = limit
        self.anchor = anchor
    }

    func execute(_ query: any ReviewedRequest) -> QueryReply {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCredentialStorage = nil
        configuration.httpCookieStorage = nil
        configuration.urlCache = nil
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 30
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        let session = URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
        var request = URLRequest(url: target)
        request.httpMethod = "POST"
        request.setValue("text/xml; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        request.setValue("\"\"", forHTTPHeaderField: "SOAPAction")
        request.httpBody = query.data
        session.dataTask(with: request).resume()
        completed.wait()
        session.invalidateAndCancel()
        if tooLarge { return QueryReply(kind: "RESPONSE_TOO_LARGE", delivery: "RESPONSE_RECEIVED", httpStatus: response?.statusCode, body: nil, responseUrl: response?.url?.absoluteString) }
        if failed { return QueryReply(kind: "UNKNOWN_DELIVERY", delivery: "UNKNOWN", httpStatus: response?.statusCode, body: nil, responseUrl: response?.url?.absoluteString) }
        guard let response else { return QueryReply(kind: "UNKNOWN_DELIVERY", delivery: "UNKNOWN", httpStatus: nil, body: nil) }
        let media = response.mimeType?.lowercased() ?? ""
        guard media == "text/xml" || media == "application/xml" || media.hasSuffix("+xml"),
              String(data: body, encoding: .utf8) != nil else {
            return QueryReply(kind: "NON_XML_RESPONSE", delivery: "RESPONSE_RECEIVED", httpStatus: response.statusCode, body: nil, responseUrl: response.url?.absoluteString)
        }
        return QueryReply(kind: "XML_RESPONSE", delivery: "RESPONSE_RECEIVED", httpStatus: response.statusCode, body: body, responseUrl: response.url?.absoluteString)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        handle(challenge, completionHandler: completionHandler)
    }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        handle(challenge, completionHandler: completionHandler)
    }

    private func handle(_ challenge: URLAuthenticationChallenge,
                        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        let protection = challenge.protectionSpace
        guard protection.host == target.host, protection.port == (target.port ?? 443), challenge.previousFailureCount == 0 else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        if protection.authenticationMethod == NSURLAuthenticationMethodClientCertificate {
            completionHandler(.useCredential, URLCredential(identity: identity, certificates: nil, persistence: .none))
        } else if protection.authenticationMethod == NSURLAuthenticationMethodServerTrust {
            if let anchor, let trust = protection.serverTrust {
                SecTrustSetAnchorCertificates(trust, [anchor] as CFArray)
                SecTrustSetAnchorCertificatesOnly(trust, true)
                let trusted = SecTrustEvaluateWithError(trust, nil)
                completionHandler(trusted ? .useCredential : .cancelAuthenticationChallenge,
                                  trusted ? URLCredential(trust: trust) : nil)
            } else {
                completionHandler(.performDefaultHandling, nil)
            }
        } else {
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        self.response = response as? HTTPURLResponse
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        if data.count > limit - body.count {
            tooLarge = true
            dataTask.cancel()
        } else {
            body.append(data)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        self.response = response
        completionHandler(nil)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        failed = error != nil
        completed.signal()
    }
}
