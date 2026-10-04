package com.ryccoatika.journeyrecorder.recorder

/**
 * Pure (JVM-testable) sensitive-input policy. Fail closed: a field whose
 * identity cannot be established is always masked.
 *
 * One instance per recording — stickiness must not leak across journeys.
 */
class SensitiveTextGuard {
    private val stickyFields = HashSet<String>()

    /**
     * Decide whether text typed into the field identified by [fieldKey] must
     * be masked.
     *
     * OR-chain: password flag; sensitive-name regex over id/hint/desc/ancestor;
     * sticky verdict from an earlier event on the same field; unknown identity
     * (null [fieldKey] or all-blank [identityStrings]) => mask.
     *
     * Once a field is judged sensitive the verdict is sticky for its key.
     */
    fun shouldMask(
        fieldKey: String?,
        isPassword: Boolean,
        identityStrings: List<String?>,
    ): Boolean {
        if (fieldKey != null && fieldKey in stickyFields) return true
        val hasIdentity = identityStrings.any { !it.isNullOrBlank() }
        val sensitive = isPassword ||
            fieldKey == null ||
            !hasIdentity ||
            identityStrings.any { it != null && SENSITIVE_NAME.containsMatchIn(it) }
        if (sensitive && fieldKey != null) stickyFields.add(fieldKey)
        return sensitive
    }

    /** Make the verdict sticky after a value-based (Luhn) hit at flush time. */
    fun markSensitive(fieldKey: String?) {
        if (fieldKey != null) stickyFields.add(fieldKey)
    }

    /**
     * Value-based check applied to the FINAL coalesced text: any 13-19 digit
     * sequence (spaces/dashes ignored) that passes Luhn => mask.
     */
    fun maskByValue(finalText: String): Boolean {
        for (match in DIGIT_RUN.findAll(finalText)) {
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19 && luhnValid(digits)) return true
        }
        return false
    }

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    private companion object {
        val SENSITIVE_NAME = Regex(
            "password|passwd|pwd|passcode|pin\\b|otp|cvv|cvc|card.?number|secret|token",
            RegexOption.IGNORE_CASE,
        )

        /** Runs of digits possibly separated by spaces/dashes. */
        val DIGIT_RUN = Regex("\\d(?:[\\d \\-]*\\d)?")
    }
}
