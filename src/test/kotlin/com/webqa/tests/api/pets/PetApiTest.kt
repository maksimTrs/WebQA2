package com.webqa.tests.api.pets

import com.webqa.tests.BaseApiTest
import com.webqa.tests.api.pets.data.PetTestDataFactory
import com.webqa.tests.api.pets.verifier.PetVerifier
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.openapitools.client.apis.PetApi
import org.openapitools.client.apis.PetApi.StatusFindPetsByStatus
import org.openapitools.client.models.Pet
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import kotlin.random.Random

@Feature("Pet Store API")
class PetApiTest : BaseApiTest() {

    private lateinit var petApi: PetApi
    private lateinit var petVerifier: PetVerifier

    @BeforeMethod
    fun setup() {
        petApi = createPetApi()
        petVerifier = PetVerifier(petApi)
    }

    @Test
    @Story("Pet Management")
    @Description("Add a new pet to the store")
    fun testAddNewPet() {
        val newPet = PetTestDataFactory.createTestPet()
        val addedPet = petApi.addPet(newPet)

        petVerifier.verifyPetDetails(addedPet, newPet)
    }

    @Test
    @Story("Pet Management")
    @Description("Update an existing pet")
    fun testUpdatePet() {
        val originalPet = PetTestDataFactory.createTestPet()
        val addedPet = petApi.addPet(originalPet)

        val updatedPet = addedPet.copy(
            name = "Updated Dog",
            status = Pet.Status.sold
        )

        val result = petApi.updatePet(updatedPet)
        assertThat(result.name).isEqualTo("Updated Dog")
        assertThat(result.status).isEqualTo(Pet.Status.sold)
    }

    @Test
    @Story("Pet Search")
    @Description("Find pets by status")
    fun testFindPetsByStatus() {
        val status = StatusFindPetsByStatus.sold
        val pets = petApi.findPetsByStatus(status)

        assertThat(pets).isNotEmpty()
        assertThat(pets.all { it.status!!.value == status.value })
            .withFailMessage("Not all returned pets have status: ${status.value}")
            .isTrue()
    }

    @Test
    @Story("Pet Search")
    @Description("Find pet by ID")
    fun testGetPetById() {
        val addedPet = petApi.addPet(PetTestDataFactory.createTestPet())
        val retrievedPet = petApi.getPetById(addedPet.id!!)

        petVerifier.verifyPetDetails(retrievedPet, addedPet)
    }

    @Test
    @Story("Pet Management")
    @Description("Delete a pet")
    fun testDeletePet() {
        val testId: Long = Random.nextLong(15_100, 100_000)
        val addedPet = petApi.addPet(PetTestDataFactory.createTestPet(testId))
        
        petApi.deletePet(addedPet.id!!)
        petVerifier.verifyPetNotFound(testId)
    }
}
