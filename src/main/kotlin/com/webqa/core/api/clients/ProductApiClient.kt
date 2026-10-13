package com.webqa.core.api.clients

import com.webqa.core.api.ApiClient
import com.webqa.core.api.models.CardDetails
import com.webqa.core.api.models.OrderItem
import com.webqa.core.api.models.OrderRequest
import com.webqa.core.api.models.ProductResponse
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.response.Response
import org.apache.http.HttpStatus.SC_OK

class ProductApiClient : ApiClient() {
    /** Happy-path helper: fails fast if the catalog is unavailable. */
    fun getProducts(authToken: String): ProductResponse {
        return Given {
            spec(requestSpec)
            header("Authorization", authToken)
        } When {
            get("/product/")
        } Then {
            statusCode(SC_OK)
        } Extract {
            `as`(ProductResponse::class.java)
        }
    }

    /** Returns the raw response: status and body are the subject of the test. */
    fun createOrder(authToken: String, productId: String, quantity: Int, card: CardDetails): Response {
        return Given {
            spec(requestSpec)
            header("Authorization", authToken)
            body(OrderRequest(card, listOf(OrderItem(productId, quantity))))
        } When {
            post("/order/createAndPay")
        }
    }
}
