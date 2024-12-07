package com.webqa.tests.api.pets


import com.webqa.tests.BaseApiTest
import io.qameta.allure.Description
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.openapitools.client.apis.PetApi
import org.openapitools.client.apis.PetApi.StatusFindPetsByStatus
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.Category
import org.openapitools.client.models.Pet
import org.openapitools.client.models.Tag
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random


class PetApiTest : BaseApiTest() {

    private lateinit var petApi: PetApi

    @BeforeMethod
    fun setup() {
        petApi = createPetApi()
    }

    @Test
    @Description("Add a new pet to the store")
    fun testAddNewPet() {
        val newPet = createTestPet()
        val addedPet = petApi.addPet(newPet)

        assertThat(addedPet.id).isNotNull()
        assertThat(addedPet.name).isEqualTo(newPet.name)
        assertThat(addedPet.category?.name).isEqualTo(newPet.category?.name)
        assertThat(addedPet.status).isEqualTo(newPet.status)
    }

    @Test
    @Description("Update an existing pet")
    fun testUpdatePet() {
        // First, create and add a new pet to the server
        val originalPet = createTestPet()
        val addedPet = petApi.addPet(originalPet)

        // Now update the pet
        val updatedPet = addedPet.copy(
            name = "Updated Dog",
            status = Pet.Status.sold
        )

        val result = petApi.updatePet(updatedPet)
        assertThat(result.name).isEqualTo("Updated Dog")
        assertThat(result.status).isEqualTo(Pet.Status.sold)
    }

    @Test
    @Description("Find pets by status")
    fun testFindPetsByStatus() {
        val status = StatusFindPetsByStatus.sold
        val pets = petApi.findPetsByStatus(status)

        assertThat(pets).isNotEmpty()
        assertThat(pets.all { it.status!!.value == status.value }).isTrue()
    }

    @Test
    @Description("Find pet by ID")
    fun testGetPetById() {
        val addedPet = petApi.addPet(createTestPet())
        val retrievedPet = petApi.getPetById(addedPet.id!!)

        assertThat(retrievedPet).isNotNull()
        assertThat(retrievedPet.id).isEqualTo(addedPet.id)
        assertThat(retrievedPet.name).isEqualTo(addedPet.name)
        assertThat(retrievedPet.status).isEqualTo(addedPet.status)
    }

    @Test
    @Description("Delete a pet")
    fun testDeletePet() {
        val testId: Long = Random.nextLong(15_100, 100_000)
        val addedPet = petApi.addPet(createTestPet(testId))
        petApi.deletePet(addedPet.id!!)

        assertThatThrownBy {
            petApi.getPetById(testId)
        }.isInstanceOf(ClientException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 404)  // Pet not found
    }

    private fun createTestPet(id: Long = ThreadLocalRandom.current().nextLong(7000, 13_000)): Pet {
        return Pet(
            id = id,
            name = "PetName$id",
            category = Category(id = 1, name = "Dogs"),
            photoUrls = listOf("string"),
            tags = listOf(Tag(id = 0, name = "testAPITag")),
            status = Pet.Status.available
        )
    }
}
