package com.webqa.tests


import com.webqa.core.config.Configuration
import com.webqa.core.driver.WebDriverFactory
import io.qameta.allure.Allure
import io.qameta.allure.Step
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
    @BeforeMethod
    @Step("Initialize WebDriver")
    fun setUp(@Optional browser: String?) {
        val browserType = WebDriverFactory.Browser.valueOf(browser?.uppercase() ?: Configuration.browser.uppercase())
        logger.info("Initializing test with browser: ${browserType.name}")

        WebDriverFactory.createDriver(browserType).also {
            if (it is RemoteWebDriver) {
                val capabilities = it.capabilities
                logger.info("Started test on ${capabilities.browserName} ${capabilities.browserVersion}")
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    @Step("Close WebDriver")
    fun tearDown(testResult: ITestResult) {
        runCatching {
            when (testResult.status) {
                ITestResult.FAILURE -> attachScreenshot()
            }
        }.onFailure {
            logger.error("Failed to capture failure evidence: ${it.message}")
        }.also {
            runCatching {
                WebDriverFactory.quitDriver()
            }.onFailure {
                logger.error("Failed to quit driver: ${it.message}")
            }
        }
    }

    protected fun getDriver(): WebDriver {
        return WebDriverFactory.getDriver()
    }


    private fun attachScreenshot() {
        runCatching {
            val screenshot = captureScreenshot()
            Allure.addAttachment(
                "Screenshot on failure",
                "image/png",
                ByteArrayInputStream(screenshot),
                "png"
            )
        }.onFailure {
            logger.error("Failed to attach screenshot: ${it.message}")
        }
    }

    private fun captureScreenshot(): ByteArray {
        return (getDriver() as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
    }
}
