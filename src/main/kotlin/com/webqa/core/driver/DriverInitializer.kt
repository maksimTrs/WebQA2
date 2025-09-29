package com.webqa.core.driver

import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

class DriverInitializer(private val options: DriverOptions) {
    private val logger = LoggerFactory.getLogger(DriverInitializer::class.java)

    fun initialize(driver: WebDriver): WebDriver {
        try {
            configureTimeouts(driver)
            configureWindow(driver)
            logger.info("Driver initialized successfully")
            return driver
        } catch (e: Exception) {
            logger.error("Failed to initialize driver: ${e.message}", e)
            try {
                driver.quit()
            } catch (quitException: Exception) {
                logger.warn("Failed to quit driver after initialization failure: ${quitException.message}")
            }
            throw DriverInitializationException("Driver initialization failed", e)
        }
    }

    private fun configureTimeouts(driver: WebDriver) {
        try {
            driver.manage().timeouts().apply {
                implicitlyWait(options.implicitWait)
                pageLoadTimeout(options.pageLoadTimeout)
                scriptTimeout(options.scriptTimeout)
            }
            logger.debug(
                "Timeouts configured - Implicit: ${options.implicitWait.seconds}s, " +
                        "PageLoad: ${options.pageLoadTimeout.seconds}s, " +
                        "Script: ${options.scriptTimeout.seconds}s"
            )
        } catch (e: Exception) {
            throw DriverInitializationException("Failed to configure timeouts", e)
        }
    }

    private fun configureWindow(driver: WebDriver) {
        try {
            val windowManager = driver.manage().window()

            when {
                options.maximized && !options.headless -> {
                    windowManager.maximize()
                    logger.debug("Window maximized")
                }
                !options.maximized -> {
                    windowManager.size = options.windowSize
                    logger.debug("Window size set to ${options.windowSize.width}x${options.windowSize.height}")
                }
                else -> {
                    logger.debug("Skipping window configuration for headless mode")
                }
            }
        } catch (e: Exception) {
            throw DriverInitializationException("Failed to configure window", e)
        }
    }
}

class DriverInitializationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)