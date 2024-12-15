package com.webqa.tests

import WebDriverFactory
import WebDriverFactory.Browser
import com.webqa.core.config.Configuration
import io.qameta.allure.Allure
import io.qameta.allure.Step
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.testng.ITestContext
import org.testng.ITestResult
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Optional
import org.testng.annotations.Parameters
import java.io.ByteArrayInputStream

abstract class BaseTest {
    protected val baseUrl = Configuration.App.baseUrl
    protected val userEmail = Configuration.App.userEmail
    protected val userPass = Configuration.App.userPass

    private lateinit var driver: WebDriver

    @Parameters("browser")
    @BeforeMethod
    @Step("Initialize WebDriver")
    fun setUp(@Optional browser: String?, context: ITestContext) {
        // TestNG parameter takes precedence over system property and config file
        val browserParam = browser?.lowercase() ?: System.getProperty("browser")?.lowercase()
        println("Initializing test with browser parameter: $browserParam")

        val browserType = when (browserParam) {
            "firefox" -> {
                println("Setting up Firefox driver")
                Browser.FIREFOX
            }

            "chrome", null -> {
                println("Setting up Chrome driver")
                Browser.CHROME
            }

            else -> {
                println("Unknown browser parameter: $browserParam, defaulting to Chrome")
                Browser.CHROME
            }
        }

        driver = WebDriverFactory.createDriver(browserType)
        context.setAttribute("WebDriver", driver)

        // Log browser info
        if (driver is RemoteWebDriver) {
            val capabilities = (driver as RemoteWebDriver).capabilities
            println("Started test on ${capabilities.browserName} ${capabilities.browserVersion}")
        }
    }

    @AfterMethod(alwaysRun = true)
    @Step("Close WebDriver")
    fun tearDown(testResult: ITestResult) {
        if (!::driver.isInitialized) {
            return
        }

        try {
            if (testResult.status == ITestResult.FAILURE) {
                attachScreenshot(testResult)
            }
        } catch (e: Exception) {
            println("Failed to capture failure evidence: ${e.message}")
        } finally {
            try {
                WebDriverFactory.quitDriver()
            } catch (e: Exception) {
                println("Failed to quit driver: ${e.message}")
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
            val screenshot = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
            Allure.addAttachment(
                "Screenshot on failure",
                "image/png",
                ByteArrayInputStream(screenshot),
                "png"
            )
        } catch (e: Exception) {
            println("Failed to attach screenshot: ${e.message}")
        }
    }
}
