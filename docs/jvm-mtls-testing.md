# JVM mTLS Transport Testing

`JvmAeatMtlsTransport` receives a ready-to-use `SSLContext` through `JvmAeatMtlsCredential`. The library never loads a key store, certificate bytes, private key, password, or path.

The normal JVM suite creates a local HTTPS server and generates fresh, disposable server/client identities for each test run. It verifies the client identity, SOAP content type, timeout classification, redacted diagnostics and the bounded-response behaviour without contacting AEAT. The generated PKCS#12 files are kept only in a temporary test directory and deleted after the test; no certificate, private key, personal credential, production record or AEAT hostname is stored in the repository.

`JvmAeatMtlsTransport` caps a retained HTTP response body at 1 MiB by default. A caller may choose a positive smaller or larger cap. When the body exceeds that cap, `ResponseTooLarge` retains the HTTP status and media type but not response bytes; its delivery state remains `RESPONSE_RECEIVED`, never fiscal acceptance. The endpoint-rejection unit test obtains an uninitialized TLS context solely as a placeholder, without loading default certificates. The runnable samples use `FakeAeatTransport` and never construct a TLS client.

`submit` expects an already-prepared SOAP payload. It is a blocking method. The existing generic network/timeout classifications and exception text are not yet sufficient for production delivery reconciliation or safe automatic logging; see [error handling](error-handling.md).
