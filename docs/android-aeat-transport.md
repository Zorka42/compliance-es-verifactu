# Android AEAT Transport

`AndroidAeatTransport` checks the existing HTTPS URL precondition and delegates already-prepared XML through `AeatTransportAdapter`. It does not validate a complete endpoint, construct SOAP, or implement an HTTP client. The Android host application supplies the adapter and owns Android KeyChain integration, certificate selection, networking configuration, certificate storage, lifecycle, and diagnostics policy.

The library does not read certificate bytes, private keys, passwords, or key-store paths. An Android unit test uses a data-only recording adapter to verify request construction; it does not use a mock framework or contact AEAT.

The adapter contract is synchronous. Execute a blocking adapter off the Android main thread. See [platform support](platform-support.md) for the distinction between host unit tests and device verification.
