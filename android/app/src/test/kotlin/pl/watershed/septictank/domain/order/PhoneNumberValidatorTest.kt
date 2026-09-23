package pl.watershed.septictank.domain.order

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberValidatorTest {

    private fun assertValid(input: String, expectedNormalized: String) =
        assertEquals(PhoneNumberValidator.Result.Valid(expectedNormalized), PhoneNumberValidator.validate(input))

    private fun assertInvalid(input: String) =
        assertTrue(input, PhoneNumberValidator.validate(input) is PhoneNumberValidator.Result.Invalid)

    @Test
    fun `strips spaces from a national number`() = assertValid("600 123 456", "600123456")

    @Test
    fun `strips spaces and dashes and keeps leading plus`() = assertValid("+48 600-123-456", "+48600123456")

    @Test
    fun `accepts exactly 9 and 15 digits`() {
        assertValid("123456789", "123456789")
        assertValid("+123456789012345", "+123456789012345")
    }

    @Test
    fun `rejects too few digits`() = assertInvalid("12345")

    @Test
    fun `rejects more than 15 digits`() = assertInvalid("1234567890123456")

    @Test
    fun `rejects letters`() = assertInvalid("60a123456")

    @Test
    fun `rejects plus not at the start`() = assertInvalid("600+123456")

    @Test
    fun `blank input means no number`() {
        assertEquals(PhoneNumberValidator.Result.Empty, PhoneNumberValidator.validate(""))
        assertEquals(PhoneNumberValidator.Result.Empty, PhoneNumberValidator.validate("   "))
    }
}
