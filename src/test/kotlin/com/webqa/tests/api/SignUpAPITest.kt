package com.webqa.tests.api

import com.webqa.core.api.clients.AuthApiClient
import com.webqa.core.api.models.SignUpRequest
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseApiTest
import io.qameta.allure.Feature
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_OK
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

@Feature("Authentication")
class SignUpAPITest : BaseApiTest() {

    private lateinit var signUpClient: AuthApiClient

    companion object {
        private const val SUCCESS_MESSAGE = "You have successfully registered"
        private const val PASSWORD_MISMATCH_MESSAGE = "Passwords do not match"
        private const val VALIDATION_ERROR_MESSAGE = "Field Validation Error"
        private const val INVALID_EMAIL = "invalid-email"
        private const val MISMATCHED_PASSWORD_1 = "password1"
        private const val MISMATCHED_PASSWORD_2 = "password2"
    }

    @BeforeMethod
    fun setup() {
        signUpClient = AuthApiClient()
    }

    @Test(description = "Verify successful user registration")
    @Story("Sign Up")
    @Severity(SeverityLevel.CRITICAL)
    fun testSuccessfulSignUp() {
        val newPassword = generatePassword()
        val request = SignUpRequest(
            email = generateEmail(),
            password = newPassword,
            passwordConfirm = newPassword
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_OK)
        assertThat(response.message)
            .withFailMessage("Expected successful registration message")
            .isEqualTo(SUCCESS_MESSAGE)
    }

    @Test(description = "Verify password mismatch validation")
    @Story("Sign Up Validation")
    @Severity(SeverityLevel.NORMAL)
    fun testSignUpWithMismatchedPasswords() {
        val request = SignUpRequest(
            email = generateEmail(),
            password = MISMATCHED_PASSWORD_1,
            passwordConfirm = MISMATCHED_PASSWORD_2
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_BAD_REQUEST)
        assertThat(response.error!!.message)
            .withFailMessage("Expected password mismatch error message")
            .isEqualTo(PASSWORD_MISMATCH_MESSAGE)
    }

    @Test(description = "Verify invalid email format validation")
    @Story("Sign Up Validation")
    @Severity(SeverityLevel.NORMAL)
    fun testSignUpWithInvalidEmail() {
        val newPassword = generatePassword()
        val request = SignUpRequest(
            email = INVALID_EMAIL,
            password = newPassword,
            passwordConfirm = newPassword
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_BAD_REQUEST)
        assertThat(response.error!!.message)
            .withFailMessage("Expected validation error for invalid email format")
            .isEqualTo(VALIDATION_ERROR_MESSAGE)
    }

    @Test(description = "Verify existing email validation")
    @Story("Sign Up Validation")
    @Severity(SeverityLevel.NORMAL)
    fun testSignUpWithExistingEmail() {
        val newPassword = generatePassword()
        val request = SignUpRequest(
            email = userEmail,
            password = newPassword,
            passwordConfirm = newPassword
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_BAD_REQUEST)
        assertThat(response.error!!.message)
            .withFailMessage("Expected duplicate email error message")
            .isEqualTo("Email $userEmail already exists")
    }
}
