# JVM mTLS Transport Testing

`JvmAeatMtlsTransport` receives a ready-to-use `SSLContext` through `JvmAeatMtlsCredential`. The library never loads a key store, certificate bytes, private key, password, or path.

For an integration test, the host application should create a local HTTPS server with non-production certificate material and provide an `SSLContext` configured with a test client certificate. This exercises mutual TLS without connecting to AEAT and without placing credentials in this repository. Normal unit tests use no mock framework and cover the deterministic endpoint, timeout, network-failure, and non-XML response classifications.

Such an mTLS integration test is separate, future work; it is not part of the normal offline suite. The endpoint-rejection unit test obtains an uninitialized TLS context solely as a placeholder, without loading default certificates. The runnable samples use `FakeAeatTransport` and never construct a TLS client.

`submit` expects an already-prepared SOAP payload. It is a blocking method. The existing generic network/timeout classifications and exception text are not yet sufficient for production delivery reconciliation or safe automatic logging; see [error handling](error-handling.md).
