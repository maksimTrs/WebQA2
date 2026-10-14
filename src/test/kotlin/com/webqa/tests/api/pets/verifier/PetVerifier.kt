package com.webqa.tests.api.pets.verifier

import io.qameta.allure.Step
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions
import org.openapitools.client.apis.PetApi
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.Pet

class PetVerifier(private val petApi: PetApi) {

    /** Soft: one failure lists every mismatched field, not only the first. */
    @Step("Verify pet details match")
    fun verifyPetDetails(actual: Pet, expected: Pet) {
        SoftAssertions.assertSoftly { soft ->
            soft.assertThat(actual.id).describedAs("pet id").isNotNull()
            soft.assertThat(actual.name).describedAs("name of pet %s", actual.id).isEqualTo(expected.name)
            soft.assertThat(actual.category?.name)
                .describedAs("category of pet %s", actual.id)
                .isEqualTo(expected.category?.name)
            soft.assertThat(actual.status).describedAs("status of pet %s", actual.id).isEqualTo(expected.status)
        }
    }

    @Step("Verify pet with ID {id} not found")
    fun verifyPetNotFound(id: Long) {
        assertThatThrownBy { petApi.getPetById(id) }
            .describedAs("GET of deleted pet %d", id)
            .isInstanceOf(ClientException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 404)
    }
}
