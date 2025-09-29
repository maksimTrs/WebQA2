package com.webqa.core.driver

import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL

object DriverHealthCheck {
    private val logger = LoggerFactory.getLogger(DriverHealthCheck::class.java)
    private const val HEALTH_CHECK_TIMEOUT_MS = 5000

    fun isGridHealthy(gridUrl: String): Boolean {
        return try {
            val statusUrl = gridUrl.replace("/wd/hub", "/status")
            logger.debug("Checking Selenium Grid health at: $statusUrl")

            val connection = URL(statusUrl).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS
                readTimeout = HEALTH_CHECK_TIMEOUT_MS
            }

            val responseCode = connection.responseCode
            val isHealthy = responseCode == 200

            if (isHealthy) {
                logger.info("Selenium Grid is healthy (HTTP $responseCode)")
            } else {
                logger.warn("Selenium Grid returned unexpected status: HTTP $responseCode")
            }

            connection.disconnect()
            isHealthy
        } catch (e: Exception) {
            logger.error("Selenium Grid health check failed: ${e.message}")
            false
        }
    }

    fun ensureGridHealthy(gridUrl: String) {
        if (!isGridHealthy(gridUrl)) {
            throw DriverCreationException(
                "Selenium Grid at $gridUrl is not healthy or unreachable. " +
                        "Please verify the grid is running and accessible."
            )
        }
    }
}