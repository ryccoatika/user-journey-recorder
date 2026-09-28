package com.ryccoatika.journeyrecorder.recorder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextGuardTest {

    private fun guard() = SensitiveTextGuard()

    private fun mask(
        fieldKey: String? = "id:com.foo:id/field",
        isPassword: Boolean = false,
        identity: List<String?> = listOf("com.foo:id/field", null, null, null),
        guard: SensitiveTextGuard = guard(),
    ): Boolean = guard.shouldMask(fieldKey, isPassword, identity)

    // ---------------------------------------------------------- password flag

    @Test
    fun `password flag masks regardless of clean identity`() {
        assertTrue(mask(isPassword = true, identity = listOf("com.foo:id/email")))
    }

    @Test
    fun `clean identity without password flag is not masked`() {
        assertFalse(mask(identity = listOf("com.foo:id/email", "Email", "Email address", null)))
    }

    // ---------------------------------------------------------- regex classes

    private fun masksKeyword(value: String): Boolean =
        mask(fieldKey = "id:$value", identity = listOf(value, null, null, null))

    @Test fun `keyword password`() = assertTrue(masksKeyword("com.foo:id/password"))

    @Test fun `keyword passwd`() = assertTrue(masksKeyword("login_passwd"))

    @Test fun `keyword pwd`() = assertTrue(masksKeyword("userPwd"))

    @Test fun `keyword passcode`() = assertTrue(masksKeyword("Enter passcode"))

    @Test fun `keyword pin with word boundary`() = assertTrue(masksKeyword("PIN code"))

    @Test fun `pin inside another word does not match`() {
        assertFalse(masksKeyword("pinterest_handle"))
        assertFalse(masksKeyword("spinner_choice"))
    }

    @Test fun `keyword otp`() = assertTrue(masksKeyword("otp_input"))

    @Test fun `keyword cvv`() = assertTrue(masksKeyword("cvv"))

    @Test fun `keyword cvc`() = assertTrue(masksKeyword("card CVC"))

    @Test fun `keyword card number variants`() {
        assertTrue(masksKeyword("cardNumber"))
        assertTrue(masksKeyword("card_number"))
        assertTrue(masksKeyword("Card number"))
    }

    @Test fun `keyword secret`() = assertTrue(masksKeyword("client_secret"))

    @Test fun `keyword token`() = assertTrue(masksKeyword("api_token"))

    @Test fun `regex is case insensitive`() = assertTrue(masksKeyword("PASSWORD"))

    @Test fun `regex applies to hint desc and ancestor too`() {
        val g = guard()
        assertTrue(g.shouldMask("k1", false, listOf("com.foo:id/f1", "Enter password", null, null)))
        assertTrue(g.shouldMask("k2", false, listOf("com.foo:id/f2", null, "OTP field", null)))
        assertTrue(g.shouldMask("k3", false, listOf("com.foo:id/f3", null, null, "secret_container")))
    }

    // ---------------------------------------------------------------- sticky

    @Test
    fun `verdict is sticky per field key`() {
        val g = guard()
        // First event: hint marks it sensitive.
        assertTrue(g.shouldMask("field", false, listOf("com.foo:id/x", "password", null, null)))
        // Later event on the SAME field with a clean identity stays masked.
        assertTrue(g.shouldMask("field", false, listOf("com.foo:id/x", "just text", null, null)))
        // A different field is unaffected.
        assertFalse(g.shouldMask("other", false, listOf("com.foo:id/y", "Email", null, null)))
    }

    @Test
    fun `markSensitive makes future events sticky`() {
        val g = guard()
        assertFalse(g.shouldMask("field", false, listOf("com.foo:id/x")))
        g.markSensitive("field")
        assertTrue(g.shouldMask("field", false, listOf("com.foo:id/x")))
    }

    // ------------------------------------------------------------ fail closed

    @Test
    fun `null field key masks`() {
        assertTrue(mask(fieldKey = null, identity = listOf("com.foo:id/clean")))
    }

    @Test
    fun `all-blank identity masks`() {
        assertTrue(mask(identity = listOf(null, null, "", "  ")))
    }

    // ------------------------------------------------------------------ Luhn

    @Test
    fun `luhn positive plain 16 digits`() {
        assertTrue(guard().maskByValue("4111111111111111"))
    }

    @Test
    fun `luhn positive with spaces`() {
        assertTrue(guard().maskByValue("4111 1111 1111 1111"))
    }

    @Test
    fun `luhn positive with dashes`() {
        assertTrue(guard().maskByValue("4111-1111-1111-1111"))
    }

    @Test
    fun `luhn positive embedded in text`() {
        assertTrue(guard().maskByValue("my card is 4111 1111 1111 1111 thanks"))
    }

    @Test
    fun `luhn negative 16 non-luhn digits`() {
        assertFalse(guard().maskByValue("1234567890123456"))
    }

    @Test
    fun `luhn negative invalid check digit`() {
        assertFalse(guard().maskByValue("4111111111111112"))
    }

    @Test
    fun `short digit runs are ignored`() {
        // 12 digits, below the 13-19 window, even though Luhn-valid.
        assertFalse(guard().maskByValue("4111 1111 1111"))
        assertFalse(guard().maskByValue("0"))
    }

    @Test
    fun `plain words are not masked by value`() {
        assertFalse(guard().maskByValue("hello world"))
        assertFalse(guard().maskByValue(""))
    }
}
