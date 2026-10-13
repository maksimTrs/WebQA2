package com.webqa.core.utils

import com.webqa.core.api.models.CardDetails
import net.datafaker.Faker
import java.time.YearMonth
import java.util.UUID

@Suppress("MagicNumber") // literal ranges describe the test data better than named constants
object TestDataGenerator {
    private val faker = Faker()

    fun generateEmail() = "test-${UUID.randomUUID()}@mail.com"

    fun generatePassword() = faker.credentials().password(6, 10)!!

    fun generateCardDetails(): CardDetails {
        // Always in the future so the card is never rejected as expired
        val expiry = YearMonth.now().plusMonths(faker.number().numberBetween(1L, 60L))
        return CardDetails(
            number = "4444${faker.number().digits(12)}",
            date = "${expiry.monthValue}:${expiry.year % 100}",
            name = faker.name().fullName().uppercase(),
            cvv = faker.number().numberBetween(100, 999)
        )
    }
}
