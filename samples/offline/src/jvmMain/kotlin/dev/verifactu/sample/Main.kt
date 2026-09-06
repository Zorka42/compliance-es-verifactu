package dev.verifactu.sample

fun main() {
    val result = runOfflineExample()
    println("Registration: ${result.responses[0].lines.single().status}")
    println("Cancellation: ${result.responses[1].lines.single().status}")
    println("Suggested wait: ${result.responses[1].retryAfterSeconds} seconds (no sleeping performed)")
    println("Next synthetic chain hash: ${result.nextChainState.hash}")
    println("Captured fake requests: ${result.capturedRequests.size}; network calls: 0")
}
