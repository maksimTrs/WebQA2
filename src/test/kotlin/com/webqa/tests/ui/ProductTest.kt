package com.webqa.tests.ui

import com.webqa.core.ui.pages.HomePage
import com.webqa.core.ui.pages.LoginPage
import com.webqa.tests.BaseTest
import io.qameta.allure.Description
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class ProductTest : BaseTest() {
    private lateinit var homePage: HomePage
    private lateinit var loginPage: LoginPage

    @BeforeMethod
    fun setup() {
        getDriver().get(baseUrl)

        // Initialize page objects after navigating to the page
        homePage = HomePage(getDriver())
        loginPage = LoginPage(getDriver())

        // Login before each test
        homePage.clickLogin()
        loginPage.login(userEmail, userPass)
        homePage.waitForLoginComplete(userEmail)

        // Wait for successful login by checking the email is displayed
        assertThat(homePage.getLoggedInUserEmail())
            .withFailMessage("Login failed or user email not displayed")
            .isEqualTo(userEmail)
    }

    @Test
    @Description("Verify products are displayed for logged in user")
    fun testProductsDisplayed() {
        assertThat(homePage.getProductsCount())
            .withFailMessage("Expected 5 products to be displayed")
            .isEqualTo(5)
    }

    @Test
    @Description("Verify product can be added to cart")
    fun testAddProductToCart() {
        homePage.addProductToCart(0)

        assertThat(homePage.isChartCartDisplayed()).isTrue
        assertThat(homePage.getChartCartInputValue()).isEqualTo(1)
    }
}
