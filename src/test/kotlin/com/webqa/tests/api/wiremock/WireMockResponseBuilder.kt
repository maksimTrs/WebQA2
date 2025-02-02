package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse

class WireMockResponseBuilder {
    private var status: Int = HttpStatusExtensions.SC_OK
    private var body: String = "{}"
    private var headers: Map<String, String> = mapOf("Content-Type" to "application/json")
    private var delayMs: Int = 0

    fun withStatus(status: Int) = apply { this.status = status }
    fun withBody(body: String) = apply { this.body = body.trimIndent() }
    fun withHeader(name: String, value: String) = apply {
        this.headers = this.headers + (name to value)
    }

    fun withDelay(delayMs: Int) = apply { this.delayMs = delayMs }

    fun build(): ResponseDefinitionBuilder {
        return aResponse()
            .withStatus(status)
            .withBody(body)
            .apply {
                headers.forEach { (name, value) -> withHeader(name, value) }
                if (delayMs > 0) withFixedDelay(delayMs)
            }
    }

    companion object {
        fun success(body: String = WireMockResponses.SUCCESS_RESPONSE) = WireMockResponseBuilder()
            .withStatus(HttpStatusExtensions.SC_OK)
            .withBody(body)

        fun error(status: Int, message: String) = WireMockResponseBuilder()
            .withStatus(status)
            .withBody(WireMockResponses.customErrorResponse(message))

        fun rateLimited() = WireMockResponseBuilder()
            .withStatus(HttpStatusExtensions.SC_TOO_MANY_REQUESTS)
            .withBody(WireMockResponses.RATE_LIMIT_RESPONSE)

        fun emailExists(email: String) = WireMockResponseBuilder()
            .withStatus(HttpStatusExtensions.SC_BAD_REQUEST)
            .withBody(WireMockResponses.emailExistsResponse(email))
    }
}
