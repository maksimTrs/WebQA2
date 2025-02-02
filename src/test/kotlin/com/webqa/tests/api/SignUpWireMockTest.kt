package com.webqa.tests.api

import com.webqa.core.api.clients.AuthApiClient
import com.webqa.core.api.models.SignUpRequest
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseApiTest
import com.webqa.tests.api.wiremock.HttpStatusExtensions
import com.webqa.tests.api.wiremock.WireMockTestExtension
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class SignUpWireMockTest : BaseApiTest(), WireMockTestExtension {
    private val logger = LoggerFactory.getLogger(SignUpWireMockTest::class.java)
    private lateinit var signUpClient: AuthApiClient

    @BeforeMethod
    fun setup() {
        val port = WireMockTestExtension.port
        logger.info("Initializing SignUpWireMockTest with WireMock port: {}", port)
        signUpClient = AuthApiClient("http://localhost:$port")
    }

    @Test(description = "Verify signup with WireMock scenario simulation")
    fun testSignUpScenario() {
        val email = generateEmail()
        logger.info("Testing signup scenario with email: {}", email)

        // Setup WireMock for email exists scenario
        stubSignUpWithScenario(email)

        val request = SignUpRequest(
            email = email,
            password = generatePassword(),
            passwordConfirm = generatePassword()
        )

        // First attempt should fail with email exists
        val response1 = signUpClient.signup(request, statusCodeVal = HttpStatusExtensions.SC_BAD_REQUEST)
        logger.info("First attempt response: {}", response1)
        assertThat(response1.error?.message).contains("already exists")

        // Second attempt should succeed
        val response2 = signUpClient.signup(request, statusCodeVal = HttpStatusExtensions.SC_OK)
        logger.info("Second attempt response: {}", response2)
        assertThat(response2.message).isEqualTo("You have successfully registered")
    }

    @Test(description = "Verify signup with simulated network delay")
    fun testSignUpWithDelay() {
        val email = generateEmail()
        val delayMs = 2000L
        logger.info("Testing signup with {}ms delay for email: {}", delayMs, email)

        // Setup WireMock with delay
        stubSignUpWithDelay(email, delayMs)

        val request = SignUpRequest(
            email = email,
            password = generatePassword(),
            passwordConfirm = generatePassword()
        )

        val startTime = System.currentTimeMillis()
        val response = signUpClient.signup(request, statusCodeVal = HttpStatusExtensions.SC_OK)
        val endTime = System.currentTimeMillis()
        val actualDelay = endTime - startTime

        logger.info("Response received after {}ms delay: {}", actualDelay, response)
        assertThat(response.message).isEqualTo("You have successfully registered")
        assertThat(actualDelay).isGreaterThanOrEqualTo(delayMs)
    }

    @Test(description = "Verify signup with rate limiting")
    fun testSignUpRateLimiting() {
        val email = generateEmail()
        logger.info("Testing rate limiting for email: {}", email)

        // Setup WireMock with rate limiting
        stubSignUpWithRateLimiting(email)

        val request = SignUpRequest(
            email = email,
            password = generatePassword(),
            passwordConfirm = generatePassword()
        )

        // First request should succeed
        val response1 = signUpClient.signup(request, statusCodeVal = HttpStatusExtensions.SC_OK)
        logger.info("First request response: {}", response1)
        assertThat(response1.message).isEqualTo("You have successfully registered")

        // Subsequent request should fail with rate limit
        val response2 = signUpClient.signup(request, statusCodeVal = HttpStatusExtensions.SC_TOO_MANY_REQUESTS)
        logger.info("Second request response: {}", response2)
        assertThat(response2.error?.message).contains("Rate limit exceeded")
    }
}
