package com.webqa.core.api.clients

import com.webqa.core.api.ApiClient
import com.webqa.core.api.models.SignUpRequest
import com.webqa.core.api.models.SignUpResponse
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.apache.http.HttpStatus.SC_OK

class AuthApiClient : ApiClient(), IAuthApiClient<String, SignUpResponse> {

    companion object {
        private const val LOGIN_ENDPOINT = "/user/login"
        private const val SIGNUP_ENDPOINT = "/user/signup"
    }

    override fun login(email: String, password: String): String {
        return Given {
            spec(requestSpec)
            body(
                mapOf(
                    "email" to email,
                    "password" to password
                )
            )
        } When {
            post(LOGIN_ENDPOINT)
        } Then {
            statusCode(SC_OK)
        } Extract {
            path<String>("authToken")
        }
    }

    override fun signup(request: SignUpRequest, statusCodeVal: Int): SignUpResponse {
        return Given {
            spec(requestSpec)
            body(request)
        } When {
            post(SIGNUP_ENDPOINT)
        } Then {
            statusCode(statusCodeVal)
        } Extract {
            `as`(SignUpResponse::class.java)
        }
    }
}
