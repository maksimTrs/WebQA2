package com.webqa.tests

import WebDriverFactory
import com.webqa.core.config.Configuration
import io.qameta.allure.Allure
import io.qameta.allure.Step
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import org.testng.ITestContext
import org.testng.ITestResult
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Optional
import org.testng.annotations.Parameters
import java.io.ByteArrayInputStream

abstract class BaseTest {
    private val logger = LoggerFactory.getLogger(this::class.java)
    protected val baseUrl = Configuration.App.baseUrl
    protected val userEmail = Configuration.App.userEmail
    protected val userPass = Configuration.App.userPass

    private lateinit var driver: WebDriver

    @Parameters("browser")
    @BeforeMethod
    @Step("Initialize WebDriver")
    fun setUp(@Optional browser: String?, context: ITestContext) {
        val browserType = WebDriverFactory.Browser.valueOf(browser?.uppercase() ?: "CHROME")
        logger.info("Initializing test with browser: ${browserType.name}")

        driver = WebDriverFactory.createDriver(browserType).also {
            context.setAttribute("WebDriver", it)
        }

        if (driver is RemoteWebDriver) {
            val capabilities = (driver as RemoteWebDriver).capabilities
            logger.info("Started test on ${capabilities.browserName} ${capabilities.browserVersion}")
        }
    }

    @AfterMethod(alwaysRun = true)
    @Step("Close WebDriver")
    fun tearDown(testResult: ITestResult) {
        if (::driver.isInitialized) {
            try {
                if (testResult.status == ITestResult.FAILURE) {
                    attachScreenshot(testResult)
                }
            } catch (e: Exception) {
                logger.error("Failed to capture failure evidence: ${e.message}")
            } finally {
                try {
                    WebDriverFactory.quitDriver()
                } catch (e: Exception) {
                    logger.error("Failed to quit driver: ${e.message}")
                }
            }
        }
    }

    protected fun getDriver(): WebDriver {
        if (!::driver.isInitialized) {
            throw IllegalStateException("WebDriver has not been initialized. Make sure setUp() is called before using getDriver()")
        }
        return driver
    }

    private fun attachScreenshot(testResult: ITestResult) {
        try {
            val screenshot = captureScreenshot()
            Allure.addAttachment(
                "Screenshot on failure",
                "image/png",
                ByteArrayInputStream(screenshot),
                "png"
            )
        } catch (e: Exception) {
            logger.error("Failed to attach screenshot: ${e.message}")
        }
    }

    private fun captureScreenshot(): ByteArray {
        return (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
    }
}
