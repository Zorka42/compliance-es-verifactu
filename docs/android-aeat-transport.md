# Android AEAT Transport

`AndroidAeatTransport` only prepares a secure AEAT request and delegates it through `AeatTransportAdapter`. The Android host application supplies the adapter and owns Android KeyChain integration, certificate selection, networking configuration, certificate storage, lifecycle, and diagnostics policy.

The library does not read certificate bytes, private keys, passwords, or key-store paths. An Android unit test uses a data-only recording adapter to verify request construction; it does not use a mock framework or contact AEAT.
