package com.webqa.tests

import com.webqa.core.config.Configuration
import com.webqa.core.driver.WebDriverFactory
import io.qameta.allure.Allure
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
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
    protected val userEmail = Configuration.App.userEmail
    protected val userPass = Configuration.App.userPass

    @Parameters("browser")
    @BeforeMethod(alwaysRun = true)
    fun setUp(@Optional browser: String?) {
        val browserName = browser?.uppercase() ?: Configuration.browser.uppercase()
        val browserType = WebDriverFactory.Browser.valueOf(browserName)
        logger.info("Initializing test with browser: {}", browserType)
        WebDriverFactory.createDriver(browserType).also { driver ->
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
            } catch (e: Exception) {
                logger.error("Failed to attach screenshot", e)
            }
        }
        try {
            WebDriverFactory.quitDriver()
        } catch (e: Exception) {
            logger.error("Failed to quit driver", e)
        }
    }

    protected fun getDriver(): WebDriver {
        return WebDriverFactory.getDriver()
    }
}
