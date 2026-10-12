package com.webqa.core.api.clients

import com.webqa.core.api.ApiClient
import com.webqa.core.api.models.SignUpRequest
import com.webqa.core.config.Configuration
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.response.Response
import org.apache.http.HttpStatus.SC_OK

class AuthApiClient(baseUrl: String = Configuration.Api.baseUrl) : ApiClient(baseUrl) {

    companion object {
        private const val LOGIN_ENDPOINT = "/user/login"
        private const val SIGNUP_ENDPOINT = "/user/signup"
    }

    /** Happy-path setup helper: fails fast if login is broken. */
    fun login(email: String, password: String): String {
        return Given {
            spec(sensitiveSpec)
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

    /** Returns the raw response: status and body are the subject of the test. */
    fun signup(request: SignUpRequest): Response {
        return Given {
            spec(requestSpec)
            body(request)
        } When {
            post(SIGNUP_ENDPOINT)
        }
    }
}
