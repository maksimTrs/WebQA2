package com.webqa.core.utils

import net.datafaker.Faker

object TestDataGenerator {
    private val faker = Faker()

    fun generateEmail() = "test${Faker().number().numberBetween(1000, 9999)}@mail.com"

    fun generatePassword() = Faker().internet().password(6, 10)

    fun generateCardDetails() = mapOf(
        "number" to "4444${faker.number().digits(12)}",
        "date" to "${faker.number().numberBetween(1, 12)}:${faker.number().numberBetween(23, 30)}",
        "name" to faker.name().fullName().uppercase(),
        "cvv" to faker.number().digits(3)
    )
}
