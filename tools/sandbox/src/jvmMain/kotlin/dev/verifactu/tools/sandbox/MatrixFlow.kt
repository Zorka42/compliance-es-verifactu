package dev.verifactu.tools.sandbox

internal fun matrixFlowControl(observations: Collection<MatrixObservation>): String {
    val exchanges = observations.filter { it.state == MatrixState.PASS }.sortedBy { it.startedAt!!.toInstant() }
    if (exchanges.size < 2) return "PENDING"
    val respected =
        exchanges.zipWithNext().all { (prior, next) ->
            !next.startedAt!!.toInstant().isBefore(prior.completedAt!!.plusSeconds(prior.waitSeconds!!).toInstant())
        }
    return if (respected) "PASS" else "REVIEW"
}
