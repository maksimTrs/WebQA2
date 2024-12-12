package com.webqa.tests.api.pets.verifier

import io.qameta.allure.Step
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.Pet
import org.openapitools.client.apis.PetApi

class PetVerifier(private val petApi: PetApi) {

    @Step("Verify pet details match")
    fun verifyPetDetails(actual: Pet, expected: Pet) {
        assertThat(actual.id).isNotNull()
        assertThat(actual.name).isEqualTo(expected.name)
        assertThat(actual.category?.name).isEqualTo(expected.category?.name)
        assertThat(actual.status).isEqualTo(expected.status)
    }

    @Step("Verify pet with ID {id} not found")
    fun verifyPetNotFound(id: Long) {
        assertThatThrownBy {
            petApi.getPetById(id)
        }.isInstanceOf(ClientException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 404)
    }
}
