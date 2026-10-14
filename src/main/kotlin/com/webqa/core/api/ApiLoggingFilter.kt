package com.webqa.core.api

import io.restassured.filter.Filter
import io.restassured.filter.FilterContext
import io.restassured.response.Response
import io.restassured.specification.FilterableRequestSpecification
import io.restassured.specification.FilterableResponseSpecification
import org.slf4j.LoggerFactory

/**
 * REST Assured logging through SLF4J: lines carry time, thread and the test name (MDC), reach the log file
 * and the per-test log in Allure. INFO is one line per call; DEBUG adds headers and bodies.
 *
 * [sensitive] calls (login: password in, token out) log the method, URI and status only.
 * Otherwise header values in [secretHeaders] and JSON fields named like a secret are masked before logging.
 */
class ApiLoggingFilter(
    private val sensitive: Boolean,
    private val secretHeaders: Set<String>
) : Filter {
    private val logger = LoggerFactory.getLogger(ApiLoggingFilter::class.java)

    override fun filter(
        requestSpec: FilterableRequestSpecification,
        responseSpec: FilterableResponseSpecification,
        ctx: FilterContext
    ): Response {
        val call = "${requestSpec.method} ${requestSpec.uri}"
        if (!sensitive && logger.isDebugEnabled) {
            logger.debug("{} headers: {}", call, maskedHeaders(requestSpec))
            requestSpec.getBody<Any?>()?.let { logger.debug("{} request body: {}", call, maskSecrets(it.toString())) }
        }

        val response = runCatching { ctx.next(requestSpec, responseSpec) }
            .onFailure { logger.warn("{} failed: {}", call, it.toString()) }
            .getOrThrow()

        logger.info("{} -> {} ({} ms)", call, response.statusCode, response.time)
        if (!sensitive && logger.isDebugEnabled) {
            logger.debug("{} response body: {}", call, truncate(maskSecrets(response.asString())))
        }
        return response
    }

    private fun maskedHeaders(requestSpec: FilterableRequestSpecification): String =
        requestSpec.headers.joinToString(", ") { header ->
            val secret = secretHeaders.any { it.equals(header.name, ignoreCase = true) }
            "${header.name}=${if (secret) MASK else header.value}"
        }

    companion object {
        private const val MASK = "***"
        private const val MAX_BODY_CHARS = 4000

        // "password": "x" in JSON and password=x in a data class toString(); also passwordConfirm, authToken
        private val SECRET_FIELD = Regex(
            """("?\b(?:password\w*|\w*[Tt]oken)"?\s*[:=]\s*)("(?:[^"\\]|\\.)*"|[^,)}\s]+)""",
            RegexOption.IGNORE_CASE
        )

        fun maskSecrets(text: String): String = SECRET_FIELD.replace(text) { "${it.groupValues[1]}\"$MASK\"" }

        private fun truncate(text: String): String =
            if (text.length <= MAX_BODY_CHARS) text else text.take(MAX_BODY_CHARS) + "... (${text.length} chars)"
    }
}
