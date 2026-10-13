package com.webqa.core.driver

import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import java.net.URL

interface DriverFactory {
    fun createDriver(browser: BrowserCapabilities, headless: Boolean): WebDriver
}

class LocalDriverFactory : DriverFactory {
    private val logger = LoggerFactory.getLogger(LocalDriverFactory::class.java)

    // Drivers are resolved by Selenium Manager (bundled with Selenium 4.6+)
    override fun createDriver(browser: BrowserCapabilities, headless: Boolean): WebDriver {
        logger.info("Creating local ${browser.browserName} driver")

        return when (browser) {
            is BrowserCapabilities.Chrome -> ChromeDriver(browser.buildCapabilities(headless))
            is BrowserCapabilities.Firefox -> FirefoxDriver(browser.buildCapabilities(headless))
        }.also {
            logger.info("Local ${browser.browserName} driver created successfully")
        }
    }
}

class RemoteDriverFactory(private val gridUrl: String) : DriverFactory {
    private val logger = LoggerFactory.getLogger(RemoteDriverFactory::class.java)

    override fun createDriver(browser: BrowserCapabilities, headless: Boolean): WebDriver {
        logger.info("Creating remote ${browser.browserName} driver at $gridUrl")

        return try {
            RemoteWebDriver(URL(gridUrl), browser.buildCapabilities(headless)).also {
                logger.info("Remote ${browser.browserName} driver created successfully")
            }
        } catch (e: WebDriverException) {
            logger.error("Failed to create remote driver: ${e.message}", e)
            throw DriverCreationException(
                "Failed to connect to Selenium Grid at $gridUrl. " +
                    "Ensure the grid is running and accessible.",
                e
            )
        }
    }
}

class DriverCreationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
