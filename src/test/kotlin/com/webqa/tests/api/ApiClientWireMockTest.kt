package com.webqa.tests.api

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.webqa.core.api.clients.AuthApiClient
import com.webqa.core.api.clients.ProductApiClient
import com.webqa.core.api.models.SignUpRequest
import com.webqa.core.api.models.SignUpResponse
import com.webqa.core.config.Configuration.Mock.WireMock
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseApiTest
import com.webqa.tests.ExpectedMessages
import com.webqa.tests.TestGroups
import com.webqa.tests.wiremock.WireMockTestExtension
import com.webqa.tests.wiremock.WireMockTestExtension.Companion.PRODUCTS_ENDPOINT
import com.webqa.tests.wiremock.WireMockTestExtension.Companion.SIGNUP_ENDPOINT
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.apache.http.HttpStatus.SC_OK
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.testng.annotations.Test
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.UUID

/**
 * Tests of the framework's own API clients, which the live stand cannot exercise:
 * the request contract they send, how the models cope with response drift, and that timeouts are applied.
 * WireMock stands in for the backend; its behavior is not the subject.
 */
@Feature("API Client")
class ApiClientWireMockTest : BaseApiTest(), WireMockTestExtension {
    private val wireMockUrl = "http://${WireMock.host}:${WireMock.port}"

    private companion object {
        // Far below the configured API_TIMEOUT so the timeout test stays fast
        val SHORT_TIMEOUT: Duration = Duration.ofSeconds(1)
        const val SLOW_RESPONSE_MS = 3000
        const val DECIMAL_PRICE = "49.99"
    }

    @Test(groups = [TestGroups.WIREMOCK])
    @Story("Request contract")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Sign-up sends the JSON body and content type the backend expects")
    fun testSignUpSendsExpectedRequest() {
        val password = generatePassword()
        val request = SignUpRequest(generateEmail(), password, password)
        stubSignUp(request.email)

        val response = AuthApiClient(wireMockUrl).signup(request).extractWithStatus<SignUpResponse>(SC_OK)

        assertThat(response.message).describedAs("stubbed registration message")
            .isEqualTo(ExpectedMessages.SIGN_UP_SUCCESS)
        verify(
            1,
            postRequestedFor(urlEqualTo(SIGNUP_ENDPOINT))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(
                    equalToJson(
                        """{"email": "${request.email}", "password": "$password", "passwordConfirm": "$password"}"""
                    )
                )
        )
    }

    @Test(groups = [TestGroups.WIREMOCK])
    @Story("Response drift")
    @Severity(SeverityLevel.NORMAL)
    @Description("Catalog models ignore undeclared fields and keep decimal prices exact; the token is sent as is")
    fun testProductsTolerateUnknownFields() {
        val authToken = "token-${UUID.randomUUID()}"
        val productId = UUID.randomUUID().toString()
        stubProducts(authToken, productId, DECIMAL_PRICE)

        val products = ProductApiClient(wireMockUrl).getProducts(authToken).products

        assertThat(products.map { it.id }).describedAs("deserialized product ids").containsExactly(productId)
        assertThat(products.single().price).describedAs("decimal price")
            .isEqualByComparingTo(BigDecimal(DECIMAL_PRICE))
        verify(1, getRequestedFor(urlEqualTo(PRODUCTS_ENDPOINT)).withHeader("Authorization", equalTo(authToken)))
    }

    @Test(groups = [TestGroups.WIREMOCK])
    @Story("Timeouts")
    @Severity(SeverityLevel.NORMAL)
    @Description("A response slower than the client timeout fails fast instead of hanging the run")
    fun testSlowResponseHitsClientTimeout() {
        val password = generatePassword()
        val request = SignUpRequest(generateEmail(), password, password)
        stubSignUp(request.email, delayMs = SLOW_RESPONSE_MS)

        val client = AuthApiClient(wireMockUrl, timeout = SHORT_TIMEOUT)
        val startedAt = System.nanoTime()

        assertThatThrownBy { client.signup(request) }
            .describedAs("signup against a %dms response with a %s timeout", SLOW_RESPONSE_MS, SHORT_TIMEOUT)
            .isInstanceOf(SocketTimeoutException::class.java)
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
            .describedAs("time until the client gave up")
            .isLessThan(Duration.ofMillis(SLOW_RESPONSE_MS.toLong()))
    }
}
