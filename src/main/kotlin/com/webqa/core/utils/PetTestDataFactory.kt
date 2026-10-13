package com.webqa.core.utils

import io.qameta.allure.Step
import org.openapitools.client.models.Category
import org.openapitools.client.models.Pet
import org.openapitools.client.models.Tag
import java.util.concurrent.ThreadLocalRandom

object PetTestDataFactory {

    @Step("Create test pet with id: {id}")
    fun createTestPet(id: Long = randomId()): Pet {
        return Pet(
            id = id,
            name = "PetName$id",
            category = createDefaultCategory(),
            photoUrls = listOf("string"),
            tags = listOf(createDefaultTag()),
            status = Pet.Status.available
        )
    }

    @Step("Create test pet with custom status: {status}")
    fun createTestPetWithStatus(
        status: Pet.Status,
        id: Long = randomId()
    ): Pet {
        return createTestPet(id).copy(status = status)
    }

    // Wide range: petstore3 is a shared public instance, small ranges collide with other users' data
    private const val MIN_ID = 1_000_000_000L
    private const val MAX_ID = 9_000_000_000L

    private fun randomId() = ThreadLocalRandom.current().nextLong(MIN_ID, MAX_ID)

    private fun createDefaultCategory() = Category(id = 1, name = "Dogs")

    private fun createDefaultTag() = Tag(id = 0, name = "testAPITag")
}
