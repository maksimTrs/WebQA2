package com.webqa.tests.ui

import com.webqa.core.ui.pages.SignUpPage
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseTest
import com.webqa.tests.ExpectedMessages
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

    @Test
    @Story("Sign Up")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify sign-up button is initially disabled")
    fun testSignUpButtonInitiallyDisabled() {
        val signUpPage = SignUpPage(getDriver()).open()

        assertThat(signUpPage.isSignUpButtonEnabled()).describedAs("sign-up button enabled on an empty form").isFalse()
    }

    @Test(groups = [TestGroups.SMOKE])
    @Story("Sign Up")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify successful user sign-up")
    fun testSuccessfulSignUp() {
        val alertText = SignUpPage(getDriver()).open()
            .fillSignUpForm(generateEmail(), generatePassword())
            .clickSignUpButton()
            .getAlertText()

        assertThat(alertText).describedAs("alert text after sign-up").isEqualTo(ExpectedMessages.SIGN_UP_SUCCESS)
    }
}
