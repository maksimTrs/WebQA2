package com.webqa.tests.wiremock

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.webqa.tests.ExpectedMessages
import org.apache.http.HttpStatus.SC_OK

/** Stubbed responses in the shape of the real API. */
object WireMockResponses {
    fun signUpSuccess(): ResponseDefinitionBuilder =
        json(SC_OK, """{"message": "${ExpectedMessages.SIGN_UP_SUCCESS}"}""")

    /**
     * A catalog the way the stand drifts: fields the models do not declare ("iamgeUrl", "createdAt")
     * and a non-integer price.
     */
    fun productsWithUnknownFields(productId: String, price: String): ResponseDefinitionBuilder = json(
        SC_OK,
        """
        {
          "message": "Products retrieved",
          "sale": 0.1,
          "createdAt": "2026-10-14T00:00:00Z",
          "products": [{
            "id": "$productId",
            "name": "Stub product",
            "description": "Served by WireMock",
            "price": $price,
            "imageUrl": "https://example.com/p.png",
            "iamgeUrl": "https://example.com/p.png",
            "stockQuantity": 3,
            "isOutOfStock": false,
            "createdAt": "2026-10-14T00:00:00Z"
          }]
        }
        """.trimIndent()
    )

    private fun json(status: Int, body: String): ResponseDefinitionBuilder = aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(body)
}
