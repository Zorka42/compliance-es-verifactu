package dev.verifactu.core

/**
 * Counts Unicode code points for XML Schema string-length facets (W3C XML Schema Part 2,
 * Second Edition, sections 4.3.1 and 4.3.3).
 *
 * A supplementary character counts once. Combining characters count separately; this function
 * does not normalize text or count displayed glyphs. It does not validate XML characters:
 * an unpaired surrogate counts as one here and is rejected separately by domain validation.
 * Some XSD providers count UTF-16 units instead; that compatibility limitation does not change
 * the deterministic common contract.
 */
public fun xmlSchemaCharacterCount(value: String): Int {
    var count = 0
    var index = 0
    while (index < value.length) {
        if (value[index] in '\uD800'..'\uDBFF' && index + 1 < value.length && value[index + 1] in '\uDC00'..'\uDFFF') {
            index++
        }
        count++
        index++
    }
    return count
}
