package dev.verifactu.tools.query

internal fun validateIssuer(issuer: String?) {
    if (issuer == null) return
    queryRequire(
        issuer.isNotBlank() && issuer.toByteArray(Charsets.UTF_8).size <= 1024 && issuer.none { it.isISOControl() },
        "--issuer must be a nonblank common name of at most 1024 UTF-8 bytes without control characters.",
    )
}
