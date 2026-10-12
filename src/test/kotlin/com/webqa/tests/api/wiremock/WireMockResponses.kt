package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_OK

/** Not defined in org.apache.http.HttpStatus (httpcore 4.4). */
const val SC_TOO_MANY_REQUESTS = 429

/** Stubbed signup responses in the shape of the real API. */
object WireMockResponses {
    fun success(): ResponseDefinitionBuilder = json(SC_OK, """{"message": "You have successfully registered"}""")

    fun rateLimited(): ResponseDefinitionBuilder =
        json(SC_TOO_MANY_REQUESTS, """{"error": {"message": "Rate limit exceeded. Try again later."}}""")

    fun emailExists(email: String): ResponseDefinitionBuilder =
        json(SC_BAD_REQUEST, """{"error": {"message": "Email $email already exists"}}""")

    private fun json(status: Int, body: String): ResponseDefinitionBuilder = aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(body)
}
