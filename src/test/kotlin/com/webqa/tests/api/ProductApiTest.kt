package com.webqa.tests.api

import com.webqa.core.api.clients.AuthApiClient
import com.webqa.core.api.clients.ProductApiClient
import com.webqa.core.api.models.ErrorResponse
import com.webqa.core.api.models.OrderResponse
import com.webqa.core.api.models.Product
import com.webqa.core.utils.TestDataGenerator
import com.webqa.tests.BaseApiTest
import com.webqa.tests.TestGroups
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_OK
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.math.BigDecimal

@Feature("Product Catalog API")
class ProductApiTest : BaseApiTest() {
    private lateinit var authApiClient: AuthApiClient
    private lateinit var productApiClient: ProductApiClient
    private lateinit var authToken: String

    companion object {
        private const val EXPECTED_SALE_PERCENTAGE = 0.1
        private const val ORDER_QUANTITY = 1
        private const val PAID_STATUS = "paid"
    }

    @BeforeClass(alwaysRun = true)
    fun setup() {
        authApiClient = AuthApiClient()
        productApiClient = ProductApiClient()
        authToken = authApiClient.login(userEmail, userPassword)
    }

    // The catalog on the shared stand changes over time: pick data from it instead of hardcoding ids and sums
    private fun firstProductInStock(): Product =
        checkNotNull(productApiClient.getProducts(authToken).products.firstOrNull { it.stockQuantity > 0 }) {
            "No product in stock on the stand: cannot place an order"
        }

    @Test(groups = [TestGroups.SMOKE])
    @Story("Product Retrieval")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify the catalog is returned and every product is consistent")
    fun testGetProducts() {
        val productsResponse = productApiClient.getProducts(authToken)

        assertThat(productsResponse.products).describedAs("products").isNotEmpty
        assertThat(productsResponse.sale).describedAs("sale").isEqualTo(EXPECTED_SALE_PERCENTAGE)

        SoftAssertions.assertSoftly { soft ->
            productsResponse.products.forEach { p ->
                soft.assertThat(p.id).describedAs("id of product '%s'", p.name).isNotBlank()
                soft.assertThat(p.name).describedAs("name of product %s", p.id).isNotBlank()
                soft.assertThat(p.price)
                    .describedAs("price of product %s", p.id)
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO)
                soft.assertThat(p.stockQuantity)
                    .describedAs("stock of product %s", p.id)
                    .isGreaterThanOrEqualTo(0)
                soft.assertThat(p.isOutOfStock)
                    .describedAs("isOutOfStock of product %s with stock %d", p.id, p.stockQuantity)
                    .isEqualTo(p.stockQuantity == 0)
            }
        }
    }

    // Mutates the shared stand: each run buys one unit and the API offers no way to roll it back
    @Test
    @Story("Order Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify an order for a product in stock is created and paid")
    fun testCreateOrder() {
        val product = firstProductInStock()
        // Sale is not applied to the expected sum: verified only for zero-price products (2026-10-12)
        val expectedSum = product.price.multiply(BigDecimal(ORDER_QUANTITY))

        val orderResponse = productApiClient
            .createOrder(authToken, product.id, ORDER_QUANTITY, TestDataGenerator.generateCardDetails())
            .extractWithStatus<OrderResponse>(SC_OK)

        val order = orderResponse.transaction.order
        SoftAssertions.assertSoftly { soft ->
            soft.assertThat(order.status).describedAs("order status").isEqualTo(PAID_STATUS)
            soft.assertThat(order.totalQuantity).describedAs("total quantity").isEqualTo(ORDER_QUANTITY)
            soft.assertThat(order.totalSum).describedAs("total sum").isEqualByComparingTo(expectedSum)
            soft.assertThat(order.products.map { it.product.id })
                .describedAs("ordered products")
                .containsExactly(product.id)
            soft.assertThat(order.user.email).describedAs("order owner").isEqualTo(userEmail)
            val sumText = order.totalSum.stripTrailingZeros().toPlainString()
            soft.assertThat(orderResponse.message)
                .describedAs("message")
                .isEqualTo("Purchase of $sumText USD completed successfully")
        }
    }

    @Test
    @Story("Order Management")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify an order exceeding the available stock is rejected")
    fun testCreateOrderExceedingStock() {
        val product = firstProductInStock()

        val error = productApiClient
            .createOrder(authToken, product.id, product.stockQuantity + 1, TestDataGenerator.generateCardDetails())
            .extractWithStatus<ErrorResponse>(SC_BAD_REQUEST)

        // Prefix only: "(requested N, available M)" can shift while testCreateOrder runs in parallel
        assertThat(error.error.message)
            .describedAs("insufficient stock error")
            .startsWith("Insufficient stock for \"${product.name}\"")
    }
}
