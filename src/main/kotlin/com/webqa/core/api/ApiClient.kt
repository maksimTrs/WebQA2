package com.webqa.core.api

import com.webqa.core.config.Configuration
import io.qameta.allure.restassured.AllureRestAssured
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.HttpClientConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.filter.log.LogDetail
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification

abstract class ApiClient(baseUrl: String = Configuration.Api.baseUrl) {
    private val timeoutMs = Configuration.Api.timeout * 1000

    protected val requestSpec: RequestSpecification = RequestSpecBuilder()
        .setBaseUri(baseUrl)
        .setContentType(ContentType.JSON)
        .setConfig(
            RestAssuredConfig.config().httpClient(
                // Apache HttpClient 4 keys (CoreConnectionPNames); RestAssured sets no timeouts by default
                HttpClientConfig.httpClientConfig()
                    .setParam("http.connection.timeout", timeoutMs)
                    .setParam("http.socket.timeout", timeoutMs)
            )
        )
        // The filter takes its blacklist from the constructor, not from LogConfig
        .addFilter(RequestLoggingFilter(LogDetail.ALL, true, System.out, true, setOf("Authorization")))
        .addFilter(ResponseLoggingFilter())
        .addFilter(AllureRestAssured())
        .build()

    init {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    }
}
