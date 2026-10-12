package com.webqa.tests.ui

import com.webqa.core.ui.pages.HomePage
import com.webqa.core.ui.pages.LoginPage
import com.webqa.tests.BaseTest
import com.webqa.tests.TestGroups
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

@Feature("Authentication")
class LoginTest : BaseTest() {
    // Getters, not fields: with parallel="methods" TestNG shares one instance across threads
    private val homePage get() = HomePage(getDriver())
    private val loginPage get() = LoginPage(getDriver())

    @BeforeMethod(alwaysRun = true)
    fun setup() {
        getDriver().get(baseUrl)
    }

    @Test(groups = [TestGroups.SMOKE])
    @Story("Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify successful login")
    fun testSuccessfulLogin() {
        homePage.clickLogin()
        loginPage.login(userEmail, userPass)

        // Wait for login to complete and email to be visible
        homePage.waitForLoginComplete(userEmail)

        assertThat(homePage.getLoggedInUserEmail()).describedAs("logged-in user email").isEqualTo(userEmail)
    }
}
