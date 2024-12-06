package com.webqa.tests.api

import com.webqa.core.api.clients.AuthApiClient
import com.webqa.core.api.clients.ProductApiClient
import com.webqa.core.utils.TestDataGenerator
import com.webqa.tests.BaseApiTest
import io.qameta.allure.Description
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class ProductApiTest : BaseApiTest() {
    private lateinit var authApiClient: AuthApiClient
    private lateinit var productApiClient: ProductApiClient
    private lateinit var authToken: String

    @BeforeClass
    fun setup() {
        authApiClient = AuthApiClient()
        productApiClient = ProductApiClient()

        // Login to get auth token
        authToken = authApiClient.login(USER_EMAIL, USER_PASSWORD)
    }

    @Test
    @Description("Verify products can be retrieved")
    fun testGetProducts() {
        val response = productApiClient.getProducts(authToken)

        assertThat(response.products)
            .isNotEmpty
            .hasSize(5)

        assertThat(response.sale)
            .isEqualTo(0.1)
    }

    @Test
    @Description("Verify order can be created")
    fun testCreateOrder() {
        val productIdVal = "1"
        val productQty = 1
        val cardDetails = TestDataGenerator.generateCardDetails()

        val orderResponse = productApiClient.createOrder(
            authToken = authToken,
            productId = productIdVal,
            quantity = productQty,
            cardDetails = cardDetails
        )

        SoftAssertions().apply {
            assertThat(orderResponse.transaction.id)
                .`as`("Transaction ID")
                .isNotNull()

            assertThat(orderResponse.transaction.order.totalQuantity)
                .`as`("Order Quantity")
                .isEqualTo(productQty)

            assertThat(orderResponse.transaction.order.id)
                .`as`("Order ID")
                .isNotNull()

            assertThat(orderResponse.transaction.order.totalSum)
                .`as`("Total Sum")
                .isEqualTo(100)

            assertThat(orderResponse.message)
                .`as`("Order Message")
                .isEqualTo("The 100usd purchase is successfully completed")

            assertThat(orderResponse.transaction.order.products[0].product.id)
                .`as`("Product ID")
                .isEqualTo(productIdVal)

        }.assertAll()
    }
}
