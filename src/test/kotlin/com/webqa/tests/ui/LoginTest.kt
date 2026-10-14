package com.webqa.tests.ui

import com.webqa.core.ui.pages.HomePage
import com.webqa.tests.BaseTest
import com.webqa.tests.TestGroups
import io.qameta.allure.Description
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Test

@Feature("Authentication")
class LoginTest : BaseTest() {

    @Test(groups = [TestGroups.SMOKE])
    @Story("Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify successful login")
    fun testSuccessfulLogin() {
        val homePage = HomePage(getDriver()).open()
            .clickLogin()
            .login(userEmail, userPass)
            .waitForLoginComplete(userEmail)

        assertThat(homePage.getLoggedInUserEmail()).describedAs("logged-in user email").isEqualTo(userEmail)
    }
}
