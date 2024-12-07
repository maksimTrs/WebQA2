package com.webqa.tests.api.pets


import com.webqa.tests.BaseApiTest
import io.qameta.allure.Description
import org.assertj.core.api.Assertions.assertThat
import org.openapitools.client.apis.PetApi
import org.openapitools.client.models.Category
import org.openapitools.client.models.Pet
import org.openapitools.client.models.Tag
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test


class PetApiTest : BaseApiTest() {

    private lateinit var petApi: PetApi


    @BeforeMethod
    fun setup() {
        petApi = createPetApi()
    }

    @Test
    @Description("Add a new pet to the store")
    fun testAddNewPet() {
        val category = Category(id = 1, name = "Dogs")
        val tag = Tag(id = 0, name = "string")
        val newPet = Pet(
            id = 10, // You might want to generate this dynamically
            name = "doggie",
            category = category,
            photoUrls = listOf("string"),
            tags = listOf(tag),
            status = Pet.Status.available
        )

        try {
            val addedPet = petApi.addPet(newPet)
            assertThat(addedPet.id).isNotNull()
            assertThat(addedPet.name).isEqualTo("doggie")
            assertThat(addedPet.category?.name).isEqualTo("Dogs")
            assertThat(addedPet.status).isEqualTo(Pet.Status.available)
        } catch (e: Exception) {
            println("Error details: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    @Description("Update an existing pet")
    fun testUpdatePet() {
        val category = Category(id = 1, name = "Dogs")
        val tag = Tag(id = 0, name = "string")
        val newPet = Pet(
            id = 10, // You might want to generate this dynamically
            name = "doggie",
            category = category,
            photoUrls = listOf("string"),
            tags = listOf(tag),
            status = Pet.Status.available
        )
        // Now update the pet
        val updatedPet = newPet.copy(
            name = "Updated Dog",
            status = Pet.Status.sold
        )

        val result = petApi.updatePet(updatedPet)
        assertThat(result.name).isEqualTo("Updated Dog")
        assertThat(result.status).isEqualTo(Pet.Status.sold)
    }

    /*
        @Test
        @Description("Finds Pets by status")
        fun testFindPetsByStatus()  {
            val status = PetApi.Status_findPetsByStatus.sold
            val pets = petApi.findPetsByStatus(status)
            Assertions.assertThat(pets).isNotEmpty()
            Assertions.assertThat(pets.all { it.status == Pet.Status.available }).isTrue()
        }
    */
}
