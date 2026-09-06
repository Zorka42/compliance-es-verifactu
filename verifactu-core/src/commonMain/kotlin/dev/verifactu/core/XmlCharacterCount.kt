package dev.verifactu.core

/**
 * Counts UTF-16 code units for compatibility with the published AEAT schema in the supported
 * XML-validator environment.
 *
 * The XML Schema `length` family is specified in characters. The JVM provider used for the
 * published AEAT XSD, however, measures supplementary characters as two units. Kotlin's
 * [String.length] has that same UTF-16 contract on every supported target, so using it prevents
 * locally accepted output from failing JVM XSD validation. A supplementary character therefore
 * consumes two units at a length boundary.
 */
public fun xmlSchemaCharacterCount(value: String): Int = value.length
