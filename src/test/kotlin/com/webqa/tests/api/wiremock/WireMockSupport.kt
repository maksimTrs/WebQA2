package com.webqa.tests.api.wiremock

import com.github.tomakehurst.wiremock.client.WireMock
import com.webqa.core.config.Configuration
import org.slf4j.LoggerFactory

object WireMockSupport {
    private val logger = LoggerFactory.getLogger(WireMockSupport::class.java)

    /** Points the static WireMock client at the Docker instance; does not start a server. */
    fun configureClient() {
        val host = Configuration.Mock.WireMock.host
        val port = Configuration.Mock.WireMock.port
        logger.info("Configuring WireMock client for Docker instance on {}:{}", host, port)
        WireMock.configureFor(host, port)
    }

    fun resetMappings() {
        logger.info("Resetting WireMock mappings")
        try {
            WireMock.reset()
        } catch (e: Exception) {
            logger.error("Error resetting WireMock: {}", e.message)
        }
    }
}
