package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass

interface WireMockTestExtension {
    companion object {
        private val logger = LoggerFactory.getLogger(WireMockTestExtension::class.java)
        private const val SCENARIO_STATE_EMAIL_CHANGED = "email-changed"
        private const val SCENARIO_STATE_STARTED = "Started"
        private const val SIGNUP_ENDPOINT = "/user/signup"

        @JvmStatic
        var port: Int = 0
    }

    @BeforeClass
    fun setupWireMock() {
        port = WireMockSupport.startWireMock()
        configureFor("localhost", port)
        logger.info("WireMock configured for localhost:{}", port)
    }

    @AfterClass
    fun tearDownWireMock() {
        logger.info("Tearing down WireMock")
        WireMockSupport.stopWireMock()
    }

    fun stubSignUpSuccess(email: String, password: String) {
        logger.info("Stubbing successful signup for email: {}", email)
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .withRequestBody(matchingJsonPath("$.password", equalTo(password)))
                .willReturn(WireMockResponseBuilder.success().build())
        )
    }

    fun stubSignUpWithScenario(email: String) {
        logger.info("Stubbing signup scenario for email: {}", email)
        // First attempt - email already exists
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario("signup-retry")
                .whenScenarioStateIs(SCENARIO_STATE_STARTED)
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponseBuilder.emailExists(email).build())
                .willSetStateTo(SCENARIO_STATE_EMAIL_CHANGED)
        )

        // Second attempt - success with different email
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario("signup-retry")
                .whenScenarioStateIs(SCENARIO_STATE_EMAIL_CHANGED)
                .willReturn(WireMockResponseBuilder.success().build())
        )
        logger.info("Signup scenario stubbed: first attempt will fail, second will succeed")
    }

    fun stubSignUpWithDelay(email: String, delayMs: Long) {
        logger.info("Stubbing signup with {}ms delay for email: {}", delayMs, email)
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(
                    WireMockResponseBuilder.success()
                        .withDelay(delayMs.toInt())
                        .build()
                )
        )
    }

    fun stubSignUpWithRateLimiting(email: String) {
        logger.info("Stubbing rate-limited signup for email: {}", email)
        // Initial state - allow the request
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario("rate-limiting")
                .whenScenarioStateIs(SCENARIO_STATE_STARTED)
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponseBuilder.success().build())
                .willSetStateTo("rate-limited")
        )

        // Rate limited state - reject subsequent requests
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario("rate-limiting")
                .whenScenarioStateIs("rate-limited")
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponseBuilder.rateLimited().build())
        )
        logger.info("Rate limiting scenario stubbed: first request will succeed, subsequent requests will be rate limited")
    }
}
