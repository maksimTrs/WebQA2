package com.webqa.tests.api.pets

import com.webqa.core.api.clients.PetStoreClient
import com.webqa.core.utils.PetTestDataFactory
import com.webqa.petstore.apis.PetApi.StatusFindPetsByStatus
import com.webqa.petstore.models.Pet
import com.webqa.tests.api.pets.verifier.PetVerifier
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
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
    @Severity(SeverityLevel.CRITICAL)
    @Description("Add a new pet to the store")
    fun testAddNewPet() {
        val newPet = PetTestDataFactory.createTestPet()
        val addedPet = addPet(newPet)

        petVerifier.verifyPetDetails(addedPet, newPet)
    }

    @Test
    @Story("Pet Management")
    @Severity(SeverityLevel.NORMAL)
    @Description("Update an existing pet")
    fun testUpdatePet() {
        val addedPet = addPet(PetTestDataFactory.createTestPet())

        val updatedPet = addedPet.copy(
            name = "Updated Dog",
            status = Pet.Status.sold
        )

        petVerifier.verifyPetDetails(petApi.updatePet(updatedPet), updatedPet)

        // The PUT response only echoes the request: read the pet back to check the update was stored
        petVerifier.verifyPetDetails(petApi.getPetById(addedPet.id!!), updatedPet)
    }

    @Test
    @Story("Pet Search")
    @Severity(SeverityLevel.NORMAL)
    @Description("Find pets by status 'pending'")
    fun testFindPetsByStatus() {
        val pendingPet = addPet(PetTestDataFactory.createTestPetWithStatus(Pet.Status.pending))

        val pets = petApi.findPetsByStatus(StatusFindPetsByStatus.pending)

        assertThat(pets.map { it.id })
            .describedAs("ids of pets found by status pending")
            .contains(pendingPet.id)
        assertThat(pets.map { it.status })
            .describedAs("statuses of pets found by status pending")
            .containsOnly(Pet.Status.pending)
    }

    @Test
    @Story("Pet Search")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Find pet by ID")
    fun testGetPetById() {
        val addedPet = addPet(PetTestDataFactory.createTestPet())
        val retrievedPet = petApi.getPetById(addedPet.id!!)

        petVerifier.verifyPetDetails(retrievedPet, addedPet)
    }

    @Test
    @Story("Pet Management")
    @Severity(SeverityLevel.NORMAL)
    @Description("Delete a pet")
    fun testDeletePet() {
        // Tracked like the others: if the delete under test fails, @AfterClass still removes the pet
        val addedPet = addPet(PetTestDataFactory.createTestPet())

        petApi.deletePet(addedPet.id!!)
        createdPetIds.remove(addedPet.id)
        petVerifier.verifyPetNotFound(addedPet.id!!)
    }
}
