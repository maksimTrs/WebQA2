package com.webqa.tests.ui

import com.webqa.core.ui.pages.HomePage
import com.webqa.tests.BaseTest
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Test

@Feature("Product Catalog")
class ProductTest : BaseTest() {

    // Called from the test body, not @BeforeMethod: a config failure would report a broken login as SKIPPED
    private fun openHomePageAsTestUser(): HomePage =
        HomePage(getDriver()).open()
            .clickLogin()
            .login(userEmail, userPass)
            .waitForLoginComplete(userEmail)

    @Test
    @Story("Product Display")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify products are displayed for logged in user")
    fun testProductsDisplayed() {
        val homePage = openHomePageAsTestUser()

        assertThat(homePage.getProductsCount()).describedAs("displayed products").isGreaterThanOrEqualTo(5)
    }

    @Test
    @Story("Shopping Cart")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify product can be added to cart")
    fun testAddProductToCart() {
        val homePage = openHomePageAsTestUser().addProductToCart(0)

        // getCartQuantity() waits for the cart and fails with "Cart is not visible" if it never appears
        assertThat(homePage.getCartQuantity()).describedAs("cart quantity after adding one product").isEqualTo(1)
    }
}
