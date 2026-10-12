package com.webqa.tests.api.pets

import com.webqa.tests.api.pets.data.PetTestDataFactory
import com.webqa.tests.api.pets.verifier.PetVerifier
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.openapitools.client.apis.PetApi.StatusFindPetsByStatus
import org.openapitools.client.models.Pet
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterClass
import org.testng.annotations.Test
import java.util.concurrent.ConcurrentHashMap

@Feature("Pet Store API")
class PetApiTest {
    private val logger = LoggerFactory.getLogger(PetApiTest::class.java)
    private val petApi = PetStoreClient.petApi
    private val petVerifier = PetVerifier(petApi)

    // Thread-safe: api-tests.xml runs methods of this instance in parallel
    private val createdPetIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @AfterClass(alwaysRun = true)
    fun deleteCreatedPets() {
        createdPetIds.forEach { id ->
            runCatching { petApi.deletePet(id) }
                .onFailure { logger.warn("Failed to delete pet {}: {}", id, it.message) }
        }
    }

    private fun addPet(pet: Pet): Pet = petApi.addPet(pet).also { createdPetIds.add(it.id!!) }

    @Test
    @Story("Pet Management")
    @Description("Add a new pet to the store")
    fun testAddNewPet() {
        val newPet = PetTestDataFactory.createTestPet()
        val addedPet = addPet(newPet)

        petVerifier.verifyPetDetails(addedPet, newPet)
    }

    @Test
    @Story("Pet Management")
    @Description("Update an existing pet")
    fun testUpdatePet() {
        val addedPet = addPet(PetTestDataFactory.createTestPet())

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
    @Description("Find pets by status 'pending'")
    fun testFindPetsByStatus() {
        val pendingPet = addPet(PetTestDataFactory.createTestPetWithStatus(Pet.Status.pending))

        val pets = petApi.findPetsByStatus(StatusFindPetsByStatus.pending)

        assertThat(pets.map { it.id })
            .withFailMessage("Created pending pet %d not found by status search", pendingPet.id)
            .contains(pendingPet.id)
        assertThat(pets)
            .withFailMessage("Not all returned pets have status: pending")
            .allMatch { it.status == Pet.Status.pending }
    }

    @Test
    @Story("Pet Search")
    @Description("Find pet by ID")
    fun testGetPetById() {
        val addedPet = addPet(PetTestDataFactory.createTestPet())
        val retrievedPet = petApi.getPetById(addedPet.id!!)

        petVerifier.verifyPetDetails(retrievedPet, addedPet)
    }

    @Test
    @Story("Pet Management")
    @Description("Delete a pet")
    fun testDeletePet() {
        val addedPet = petApi.addPet(PetTestDataFactory.createTestPet())

        petApi.deletePet(addedPet.id!!)
        petVerifier.verifyPetNotFound(addedPet.id!!)
    }
}
