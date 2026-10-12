package com.webqa.core.utils

import net.datafaker.Faker
import java.time.YearMonth
import java.util.UUID

@Suppress("MagicNumber") // literal ranges describe the test data better than named constants
object TestDataGenerator {
    private val faker = Faker()

    fun generateEmail() = "test-${UUID.randomUUID()}@mail.com"

    fun generatePassword() = faker.internet().password(6, 10)!!

    fun generateCardDetails(): Map<String, Any> {
        // Always in the future so the card is never rejected as expired
        val expiry = YearMonth.now().plusMonths(faker.number().numberBetween(1L, 60L))
        return mapOf(
            "number" to "4444${faker.number().digits(12)}",
            "date" to "${expiry.monthValue}:${expiry.year % 100}",
            "name" to faker.name().fullName().uppercase(),
            "cvv" to faker.number().numberBetween(100, 999)
        )
    }
}
