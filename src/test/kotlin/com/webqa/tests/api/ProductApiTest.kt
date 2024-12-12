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

    companion object {
        private const val EXPECTED_PRODUCTS_COUNT = 5
        private const val EXPECTED_SALE_PERCENTAGE = 0.1
        private const val DEFAULT_PRODUCT_ID = "1"
        private const val DEFAULT_PRODUCT_QUANTITY = 1
        private const val EXPECTED_TOTAL_SUM = 100
        private const val SUCCESSFUL_PURCHASE_MESSAGE = "The 100usd purchase is successfully completed"
    }

    @BeforeClass
    fun setup() {
        authApiClient = AuthApiClient()
        productApiClient = ProductApiClient()
        authToken = authApiClient.login(userEmail, userPassword)
    }

    @Test
    @Description("Verify products can be retrieved")
    fun testGetProducts() {
        val productsResponse = productApiClient.getProducts(authToken)

        assertThat(productsResponse.products)
            .withFailMessage("Products list should not be empty")
            .isNotEmpty
            .hasSize(EXPECTED_PRODUCTS_COUNT)

        assertThat(productsResponse.sale)
            .withFailMessage("Sale percentage should match expected value")
            .isEqualTo(EXPECTED_SALE_PERCENTAGE)
    }

    @Test
    @Description("Verify order can be created")
    fun testCreateOrder() {
        val cardDetails = TestDataGenerator.generateCardDetails()

        val orderResponse = productApiClient.createOrder(
            authToken = authToken,
            productId = DEFAULT_PRODUCT_ID,
            quantity = DEFAULT_PRODUCT_QUANTITY,
            cardDetails = cardDetails
        )

        SoftAssertions().apply {
            assertThat(orderResponse.transaction.id).isNotNull()
            assertThat(orderResponse.transaction.order.totalQuantity).isEqualTo(DEFAULT_PRODUCT_QUANTITY)
            assertThat(orderResponse.transaction.order.id).isNotNull()
            assertThat(orderResponse.transaction.order.totalSum).isEqualTo(EXPECTED_TOTAL_SUM)
            assertThat(orderResponse.message).isEqualTo(SUCCESSFUL_PURCHASE_MESSAGE)
            assertThat(orderResponse.transaction.order.products[0].product.id).isEqualTo(DEFAULT_PRODUCT_ID)
        }.assertAll()
    }
}
