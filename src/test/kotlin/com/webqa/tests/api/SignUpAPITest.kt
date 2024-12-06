package com.webqa.tests.api

import com.webqa.core.api.clients.AuthApiClient
import com.webqa.core.api.models.SignUpRequest
import com.webqa.core.utils.TestDataGenerator.generateEmail
import com.webqa.core.utils.TestDataGenerator.generatePassword
import com.webqa.tests.BaseApiTest
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_OK
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class SignUpAPITest : BaseApiTest() {

    private lateinit var signUpClient: AuthApiClient

    @BeforeMethod
    fun setup() {
        signUpClient = AuthApiClient()
    }

    @Test
    fun testSuccessfulSignUp() {
        val newPassword = generatePassword()
        val request = SignUpRequest(
            email = generateEmail(),
            password = newPassword,
            passwordConfirm = newPassword
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_OK)
        assertThat(response.message).isEqualTo("You have successfully registered")
    }

    @Test(description = "Verify password mismatch")
    fun testSignUpWithMismatchedPasswords() {
        val request = SignUpRequest(
            email = generateEmail(),
            password = "password1",
            passwordConfirm = "password2"
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_BAD_REQUEST)
        assertThat(response.error!!.message).isEqualTo("Passwords do not match")
    }

    @Test(description = "Verify invalid email format")
    fun testSignUpWithInvalidEmail() {
        val newPassword = generatePassword()
        val request = SignUpRequest(
            email = "invalid-email",
            password = newPassword,
            passwordConfirm = newPassword
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_BAD_REQUEST)
        assertThat(response.error!!.message).isEqualTo("Field Validation Error")
    }

    @Test(description = "Verify existing email validation")
    fun testSignUpWithExistingEmail() {
        val newPassword = generatePassword()
        val request = SignUpRequest(
            email = USER_EMAIL,
            password = newPassword,
            passwordConfirm = newPassword
        )

        val response = signUpClient.signup(request, statusCodeVal = SC_BAD_REQUEST)
        assertThat(response.error!!.message).isEqualTo("Email $USER_EMAIL already exists")
    }
}
