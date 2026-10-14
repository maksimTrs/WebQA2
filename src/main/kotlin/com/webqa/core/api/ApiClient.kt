package com.webqa.core.api

import com.webqa.core.config.Configuration
import io.qameta.allure.restassured.AllureRestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.HttpClientConfig
import io.restassured.config.LogConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.filter.log.LogDetail
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import java.time.Duration

abstract class ApiClient(
    private val baseUrl: String = Configuration.Api.baseUrl,
    timeout: Duration = Duration.ofSeconds(Configuration.Api.timeout.toLong())
) {
    private val timeoutMs = timeout.toMillis().toInt()

    protected val requestSpec: RequestSpecification = buildSpec(sensitive = false)

    /**
     * For calls that carry secrets both ways (login: password in the request, token in the response):
     * only the URI and the status are logged, nothing is attached to Allure.
     */
    protected val sensitiveSpec: RequestSpecification = buildSpec(sensitive = true)

    private fun buildSpec(sensitive: Boolean): RequestSpecification = RequestSpecBuilder()
        .setBaseUri(baseUrl)
        .setContentType(ContentType.JSON)
        .setConfig(
            RestAssuredConfig.config()
                .httpClient(
                    // Apache HttpClient 4 keys (CoreConnectionPNames); RestAssured sets no timeouts by default
                    HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", timeoutMs)
                        .setParam("http.socket.timeout", timeoutMs)
                )
                // AllureRestAssured masks the headers listed here; RequestLoggingFilter ignores this list
                .logConfig(LogConfig.logConfig().blacklistHeaders(SECRET_HEADERS))
        )
        .apply {
            if (sensitive) {
                addFilter(RequestLoggingFilter(LogDetail.URI))
                addFilter(ResponseLoggingFilter(LogDetail.STATUS))
            } else {
                addFilter(RequestLoggingFilter(LogDetail.ALL, true, System.out, true, SECRET_HEADERS))
                addFilter(ResponseLoggingFilter())
                addFilter(AllureRestAssured())
            }
        }
        .build()

    private companion object {
        val SECRET_HEADERS = setOf("Authorization")
    }
}
