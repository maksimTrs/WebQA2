package com.webqa.tests.ui

import com.webqa.core.ui.pages.SignUpPage
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseTest
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class SignUpUITest : BaseTest() {
    private lateinit var signUpPage: SignUpPage

    @BeforeMethod
    fun openSignUpPage() {
        signUpPage = SignUpPage(getDriver())
    }

    @Test
    fun testSignUpButtonInitiallyDisabled() {
        signUpPage.open()
        assertThat(signUpPage.isSignUpButtonEnabled()).isFalse()
    }

    @Test
    fun testSuccessfulSignUp() {
        val email = generateEmail()
        val password = generatePassword()

        signUpPage.open()
        signUpPage.fillSignUpForm(email, password)
        signUpPage.clickSignUpButton()

        assertThat(signUpPage.isAlertPresent()).isTrue()
        assertThat(signUpPage.getAlertText()).isEqualTo("You have successfully registered")
    }
}
