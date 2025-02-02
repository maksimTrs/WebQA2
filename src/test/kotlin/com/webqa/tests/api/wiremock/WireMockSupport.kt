package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.WireMock
import com.webqa.core.config.Configuration
import org.slf4j.LoggerFactory

object WireMockSupport {
    private val logger = LoggerFactory.getLogger(WireMockSupport::class.java)

    fun startWireMock(): Int {
        val host = Configuration.Mock.WireMock.host
        val port = Configuration.Mock.WireMock.port
        logger.info("Configuring WireMock client for Docker instance on {}:{}", host, port)
        WireMock.configureFor(host, port)
        return port
    }

    fun stopWireMock() {
        logger.info("Resetting WireMock mappings")
        try {
            WireMock.reset()
        } catch (e: Exception) {
            logger.error("Error resetting WireMock: {}", e.message)
        }
    }

    fun resetWireMock() {
        logger.info("Resetting WireMock mappings")
        try {
            WireMock.reset()
        } catch (e: Exception) {
            logger.error("Error resetting WireMock: {}", e.message)
        }
    }
}
