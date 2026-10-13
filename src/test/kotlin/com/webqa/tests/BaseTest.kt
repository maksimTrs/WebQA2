package com.webqa.tests

import com.webqa.core.config.Configuration
import com.webqa.core.driver.BrowserCapabilities
import com.webqa.core.driver.WebDriverFactory
import io.qameta.allure.Allure
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import org.testng.ITestResult
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Optional
import org.testng.annotations.Parameters
import java.io.ByteArrayInputStream

abstract class BaseTest {
    private val logger = LoggerFactory.getLogger(this::class.java)
    protected val baseUrl = Configuration.App.baseUrl

    // Lazy: tests that never log in (e.g. sign-up) must not require credentials
    protected val userEmail: String by lazy { Configuration.Credentials.userEmail }
    protected val userPass: String by lazy { Configuration.Credentials.password }

    @Parameters("browser")
    @BeforeMethod(alwaysRun = true)
    fun setUp(@Optional browser: String?) {
        val browserName = browser?.lowercase() ?: Configuration.browser.lowercase()
        val browserCapabilities = BrowserCapabilities.fromString(browserName)
        logger.info("Initializing test with browser: {}", browserCapabilities.browserName)

        WebDriverFactory.createDriver(browserCapabilities, Configuration.headless).also { driver ->
            if (driver is RemoteWebDriver) {
                logger.info(
                    "Started test on {} {}",
                    driver.capabilities.browserName,
                    driver.capabilities.browserVersion
                )
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    fun tearDown(result: ITestResult) {
        if (result.status == ITestResult.FAILURE) {
            try {
                val screenshot = (getDriver() as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
                Allure.addAttachment(
                    "Screenshot on failure",
                    "image/png",
                    ByteArrayInputStream(screenshot),
                    "png"
                )
            } catch (e: WebDriverException) {
                logger.error("Failed to attach screenshot", e)
            }
        }
        WebDriverFactory.quitDriver()
    }

    protected fun getDriver(): WebDriver {
        return WebDriverFactory.getDriver()
    }
}
