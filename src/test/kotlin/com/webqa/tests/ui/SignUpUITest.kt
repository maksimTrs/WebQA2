package com.webqa.tests.ui

import com.webqa.core.ui.pages.SignUpPage
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
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
class SignUpUITest : BaseTest() {
    // Getter, not a field: with parallel="methods" TestNG shares one instance across threads
    private val signUpPage get() = SignUpPage(getDriver())

    @Test
    @Story("Sign Up")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify sign-up button is initially disabled")
    fun testSignUpButtonInitiallyDisabled() {
        signUpPage.open()
        assertThat(signUpPage.isSignUpButtonEnabled()).describedAs("sign-up button enabled on an empty form").isFalse()
    }

    @Test(groups = [TestGroups.SMOKE])
    @Story("Sign Up")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify successful user sign-up")
    fun testSuccessfulSignUp() {
        val email = generateEmail()
        val password = generatePassword()

        signUpPage.open()
        signUpPage.fillSignUpForm(email, password)
        signUpPage.clickSignUpButton()

        assertThat(signUpPage.isAlertPresent()).describedAs("alert shown after sign-up").isTrue()
        assertThat(signUpPage.getAlertText()).describedAs("alert text").isEqualTo("You have successfully registered")
    }
}
