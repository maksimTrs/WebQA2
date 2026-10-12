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
import java.time.Duration

abstract class ApiClient(private val baseUrl: String = Configuration.Api.baseUrl) {
    private val timeoutMs = Duration.ofSeconds(Configuration.Api.timeout.toLong()).toMillis().toInt()

    protected val requestSpec: RequestSpecification = buildSpec(logResponseBody = true)

    /** For responses that carry secrets (auth tokens): response body is neither logged nor attached to Allure. */
    protected val sensitiveResponseSpec: RequestSpecification = buildSpec(logResponseBody = false)

    init {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    }

    private fun buildSpec(logResponseBody: Boolean): RequestSpecification = RequestSpecBuilder()
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
        .apply {
            if (logResponseBody) {
                addFilter(ResponseLoggingFilter())
                addFilter(AllureRestAssured())
            } else {
                addFilter(ResponseLoggingFilter(LogDetail.STATUS))
            }
        }
        .build()
}
