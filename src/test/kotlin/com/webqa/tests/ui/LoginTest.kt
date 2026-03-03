package com.webqa.tests.ui

import com.webqa.core.ui.pages.HomePage
import com.webqa.core.ui.pages.LoginPage
import com.webqa.tests.BaseTest
import io.qameta.allure.*
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

@Feature("Authentication")
class LoginTest : BaseTest() {
    private lateinit var homePage: HomePage
    private lateinit var loginPage: LoginPage

    @BeforeMethod
    fun setup() {
        homePage = HomePage(getDriver())
        loginPage = LoginPage(getDriver())
        getDriver().get(baseUrl)
    }

    @Test
    @Story("Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify successful login")
    fun testSuccessfulLogin() {
        homePage.clickLogin()
        loginPage.login(userEmail, userPass)

        // Wait for login to complete and email to be visible
        homePage.waitForLoginComplete(userEmail)

        assertThat(homePage.getLoggedInUserEmail())
            .withFailMessage("Expected logged-in user email to be '$userEmail'")
            .isEqualTo(userEmail)
    }
}
