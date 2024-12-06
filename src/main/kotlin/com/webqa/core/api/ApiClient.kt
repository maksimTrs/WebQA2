package com.webqa.core.api

import com.webqa.core.config.Configuration
import io.qameta.allure.restassured.AllureRestAssured
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification

abstract class ApiClient {
    protected val requestSpec: RequestSpecification = RequestSpecBuilder()
        .setBaseUri(Configuration.Api.baseUrl)
        .setContentType(ContentType.JSON)
        .addFilter(RequestLoggingFilter())
        .addFilter(ResponseLoggingFilter())
        .addFilter(AllureRestAssured())
        .build()

    init {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    }

    protected fun withAuth(token: String): RequestSpecification {
        return requestSpec.header("Authorization", token)
    }
}
