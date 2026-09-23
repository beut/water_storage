package pl.watershed.septictank.domain.order

/**
 * Validates and normalizes the pumping company phone number (spec 003 FR-002, data-model.md ->
 * TankConfiguration.pumpingCompanyPhone).
 */
object PhoneNumberValidator {

    sealed interface Result {
        data class Valid(val normalized: String) : Result
        data object Empty : Result
        data class Invalid(val reason: String) : Result
    }

    private val ALLOWED_INPUT = Regex("^\\+?[0-9 \\-]*$")
    private val NORMALIZED = Regex("^\\+?[0-9]{9,15}$")

    fun validate(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Empty
        if (!ALLOWED_INPUT.matches(trimmed)) {
            return Result.Invalid("Numer może zawierać tylko cyfry, spacje, myślniki i + na początku")
        }
        val normalized = trimmed.replace(" ", "").replace("-", "")
        if (!NORMALIZED.matches(normalized)) return Result.Invalid("Numer musi mieć od 9 do 15 cyfr")
        return Result.Valid(normalized)
    }
}
