package com.webqa.tests

import com.webqa.core.config.Configuration
import com.webqa.core.driver.BrowserCapabilities
import com.webqa.core.driver.DriverSession
import com.webqa.core.driver.WebDriverFactory
import io.qameta.allure.Allure
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.logging.LogType
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

    // Lazy: tests that never log in (e.g. sign-up) must not require credentials
    protected val userEmail: String by lazy { Configuration.Credentials.userEmail }
    protected val userPass: String by lazy { Configuration.Credentials.password }

    @Parameters("browser")
    @BeforeMethod(alwaysRun = true)
    fun setUp(@Optional browser: String?) {
        val browserName = browser?.lowercase() ?: Configuration.WebDriver.browser.lowercase()
        val browserCapabilities = BrowserCapabilities.fromString(browserName)
        logger.info("Initializing test with browser: {}", browserCapabilities.browserName)

        WebDriverFactory.createDriver(browserCapabilities).also { driver ->
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
        try {
            if (result.status == ITestResult.FAILURE) {
                attachFailureArtifacts(getDriver())
            }
        } finally {
            DriverSession.quit()
        }
    }

    protected fun getDriver(): WebDriver {
        return DriverSession.get()
    }

    /** Each attachment on its own: a browser that cannot provide one (Firefox has no console log) keeps the rest. */
    private fun attachFailureArtifacts(driver: WebDriver) {
        attach("URL on failure") {
            Allure.addAttachment("URL on failure", "text/uri-list", driver.currentUrl.orEmpty())
        }
        attach("Screenshot on failure") {
            val screenshot = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
            Allure.addAttachment("Screenshot on failure", "image/png", ByteArrayInputStream(screenshot), "png")
        }
        // React mirrors typed input into the value attribute, so a failure on a form would put the password
        // into the HTML. If the script fails, attach() skips the page source rather than attach it unmasked
        attach("Page source on failure") {
            (driver as JavascriptExecutor).executeScript(
                "document.querySelectorAll('input[type=password]').forEach(e => e.removeAttribute('value'))"
            )
            Allure.addAttachment("Page source on failure", "text/html", driver.pageSource.orEmpty(), "html")
        }
        // Chrome only: BrowserCapabilities.Chrome enables goog:loggingPrefs
        attach("Browser console on failure") {
            val entries = driver.manage().logs().get(LogType.BROWSER).all
            if (entries.isNotEmpty()) {
                Allure.addAttachment("Browser console on failure", "text/plain", entries.joinToString("\n"), "log")
            }
        }
    }

    private fun attach(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: WebDriverException) {
            logger.warn("Could not attach '{}': {}", name, e.message)
        }
    }
}
