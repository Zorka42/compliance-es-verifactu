# Apple AEAT Transport

`AppleAeatTransport` is available in the shared `appleMain` source set for iOS and macOS. It accepts the common `AeatTransportAdapter` contract, checks the prepared endpoint for the existing HTTPS precondition, and delegates the exact XML request.

The host owns Apple networking and authentication, including any URLSession delegate and Keychain integration. This repository does not implement those platform facilities or inspect/import certificates. The abstraction is an explicit injection boundary, not a built-in Apple HTTP/mTLS client.

The contract is synchronous. A blocking host adapter must execute on a worker thread; never block the main thread while waiting for callbacks that require it. An async native adapter API remains a future design decision.

Apple tests in `appleTest` exercise request forwarding, error propagation, and rejecting insecure endpoints before delegation. Shared record/hash/XML/QR and testkit tests also run on iOS simulator and macOS. No Apple test needs a real certificate or AEAT connectivity.

Run on a configured Mac:

```bash
./gradlew iosSimulatorArm64Test macosArm64Test
```

See [platform support](platform-support.md) and [integration responsibilities](integration-responsibilities.md). Production SOAP construction and delivery-state interpretation remain separate pending runtime work.
