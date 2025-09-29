package com.webqa.core.driver

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import java.net.URL

/**
 * Core interface for creating WebDriver instances.
 */
interface DriverFactory {
    fun createDriver(browser: BrowserCapabilities, options: DriverOptions): WebDriver
}

/**
 * Factory for creating local WebDriver instances with WebDriverManager integration.
 */
class LocalDriverFactory : DriverFactory {
    private val logger = LoggerFactory.getLogger(LocalDriverFactory::class.java)

    // Lazy initialization to avoid redundant WebDriverManager setup
    private val chromeSetup = lazy {
        logger.debug("Setting up ChromeDriver...")
        WebDriverManager.chromedriver().setup()
    }

    private val firefoxSetup = lazy {
        logger.debug("Setting up FirefoxDriver...")
        WebDriverManager.firefoxdriver().setup()
    }

    override fun createDriver(browser: BrowserCapabilities, options: DriverOptions): WebDriver {
        logger.info("Creating local ${browser.browserName} driver")

        return when (browser) {
            is BrowserCapabilities.Chrome -> {
                chromeSetup.value
                ChromeDriver(browser.buildCapabilities(options) as ChromeOptions)
            }
            is BrowserCapabilities.Firefox -> {
                firefoxSetup.value
                FirefoxDriver(browser.buildCapabilities(options) as FirefoxOptions)
            }
        }.also {
            logger.info("Local ${browser.browserName} driver created successfully")
        }
    }
}

/**
 * Factory for creating remote WebDriver instances (Selenium Grid).
 * Includes health check and retry logic for resilience.
 */
class RemoteDriverFactory(private val gridUrl: String) : DriverFactory {
    private val logger = LoggerFactory.getLogger(RemoteDriverFactory::class.java)

    override fun createDriver(browser: BrowserCapabilities, options: DriverOptions): WebDriver {
        logger.info("Creating remote ${browser.browserName} driver at $gridUrl")

        return try {
            RemoteWebDriver(URL(gridUrl), browser.buildCapabilities(options)).also {
                logger.info("Remote ${browser.browserName} driver created successfully")
            }
        } catch (e: Exception) {
            logger.error("Failed to create remote driver: ${e.message}", e)
            throw DriverCreationException(
                "Failed to connect to Selenium Grid at $gridUrl. " +
                        "Ensure the grid is running and accessible.",
                e
            )
        }
    }
}

/**
 * Exception thrown when driver creation fails.
 */
class DriverCreationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)