package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass

/**
 * Lifecycle and stubs for tests against the WireMock container.
 * Every stub matches a value unique to its test (email, token): a leftover stub never answers another test.
 */
interface WireMockTestExtension {
    companion object {
        private val logger = LoggerFactory.getLogger(WireMockTestExtension::class.java)
        const val SIGNUP_ENDPOINT = "/user/signup"
        const val PRODUCTS_ENDPOINT = "/product/"
    }

    // Reset before, not only after: an interrupted run leaves stubs in the long-lived container
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

    fun stubSignUp(email: String, delayMs: Int = 0) {
        logger.info("Stubbing signup for {} with {}ms delay", email, delayMs)
        stubFor(
            post(urlEqualTo(SIGNUP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .willReturn(WireMockResponses.signUpSuccess().withFixedDelay(delayMs))
        )
    }

    /** Answers only a request carrying [authToken]: without the header the client gets WireMock's 404. */
    fun stubProducts(authToken: String, productId: String, price: String) {
        logger.info("Stubbing products for product {}", productId)
        stubFor(
            get(urlEqualTo(PRODUCTS_ENDPOINT))
                .withHeader("Authorization", equalTo(authToken))
                .willReturn(WireMockResponses.productsWithUnknownFields(productId, price))
        )
    }
}
