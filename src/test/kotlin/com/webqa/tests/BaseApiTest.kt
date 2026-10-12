package com.webqa.tests

import com.webqa.core.config.Configuration.App
import io.restassured.response.Response
import org.assertj.core.api.Assertions.assertThat

abstract class BaseApiTest {
    // Lazy: tests that never log in (e.g. WireMock) must not require credentials
    protected val userEmail: String by lazy { App.userEmail }
    protected val userPassword: String by lazy { App.userPass }

    /** Asserts the status code (with the body in the failure message) and deserializes the body. */
    protected inline fun <reified T> Response.extractWithStatus(expectedStatus: Int): T {
        assertThat(statusCode)
            .withFailMessage("Expected status %d but was %d. Body: %s", expectedStatus, statusCode, asString())
            .isEqualTo(expectedStatus)
        return `as`(T::class.java)
    }
}
