package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass

interface WireMockTestExtension {
    companion object {
        private val logger = LoggerFactory.getLogger(WireMockTestExtension::class.java)
        private const val SCENARIO_STATE_EMAIL_CHANGED = "email-changed"
        private const val SCENARIO_STATE_STARTED = "Started"
        private const val SIGNUP_ENDPOINT = "/user/signup"
    }

    // Reset before, not only after: an interrupted run leaves stubs and scenario states in the long-lived container
    @BeforeClass(alwaysRun = true)
    fun setupWireMock() {
        WireMockSupport.configureClient()
        WireMockSupport.resetMappings()
    }

    @AfterClass(alwaysRun = true)
    fun tearDownWireMock() {
        logger.info("Tearing down WireMock")
        WireMockSupport.resetMappings()
    }

    fun stubSignUpWithScenario(email: String) {
        logger.info("Stubbing signup scenario for email: {}", email)
        // Scenario per email and an email matcher on every stub: a leftover stub must not answer another test's request
        val scenario = "signup-retry-$email"
        // First attempt - email already exists
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario(scenario)
                .whenScenarioStateIs(SCENARIO_STATE_STARTED)
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponses.emailExists(email))
                .willSetStateTo(SCENARIO_STATE_EMAIL_CHANGED)
        )

        // Second attempt with the same email - success
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario(scenario)
                .whenScenarioStateIs(SCENARIO_STATE_EMAIL_CHANGED)
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponses.success())
        )
        logger.info("Signup scenario stubbed: first attempt will fail, second will succeed")
    }

    fun stubSignUpWithDelay(email: String, delayMs: Long) {
        logger.info("Stubbing signup with {}ms delay for email: {}", delayMs, email)
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponses.success().withFixedDelay(delayMs.toInt()))
        )
    }

    fun stubSignUpWithRateLimiting(email: String) {
        logger.info("Stubbing rate-limited signup for email: {}", email)
        val scenario = "rate-limiting-$email"
        // Initial state - allow the request
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario(scenario)
                .whenScenarioStateIs(SCENARIO_STATE_STARTED)
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponses.success())
                .willSetStateTo("rate-limited")
        )

        // Rate limited state - reject subsequent requests
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .inScenario(scenario)
                .whenScenarioStateIs("rate-limited")
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponses.rateLimited())
        )
        logger.info(
            "Rate limiting scenario stubbed: first request will succeed, subsequent requests will be rate limited"
        )
    }
}
