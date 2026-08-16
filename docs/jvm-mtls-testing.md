# JVM mTLS Transport Testing

`JvmAeatMtlsTransport` receives a ready-to-use `SSLContext` through `JvmAeatMtlsCredential`. The library never loads a key store, certificate bytes, private key, password, or path.

For an integration test, the host application should create a local HTTPS server with non-production certificate material and provide an `SSLContext` configured with a test client certificate. This exercises mutual TLS without connecting to AEAT and without placing credentials in this repository. Normal unit tests use no mock framework and cover the deterministic endpoint, timeout, network-failure, and non-XML response classifications.
