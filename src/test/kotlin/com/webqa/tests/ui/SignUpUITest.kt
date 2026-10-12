package com.webqa.tests.ui

import com.webqa.core.ui.pages.SignUpPage
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseTest
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
        assertThat(signUpPage.isSignUpButtonEnabled())
            .withFailMessage("Sign-up button should be disabled before filling the form")
            .isFalse()
    }

    @Test
    @Story("Sign Up")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify successful user sign-up")
    fun testSuccessfulSignUp() {
        val email = generateEmail()
        val password = generatePassword()

        signUpPage.open()
        signUpPage.fillSignUpForm(email, password)
        signUpPage.clickSignUpButton()

        assertThat(signUpPage.isAlertPresent())
            .withFailMessage("Success alert should be displayed after sign-up")
            .isTrue()
        assertThat(signUpPage.getAlertText())
            .withFailMessage("Alert text should confirm successful registration")
            .isEqualTo("You have successfully registered")
    }
}
