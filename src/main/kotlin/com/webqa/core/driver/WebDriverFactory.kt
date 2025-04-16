package com.webqa.core.driver

import com.webqa.core.config.Configuration
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.Dimension
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import java.net.URL

object WebDriverFactory {
    private val logger = LoggerFactory.getLogger(WebDriverFactory::class.java)
    private val driverThreadLocal = ThreadLocal<WebDriver>()
    private const val DEFAULT_WINDOW_WIDTH = 1920
    private const val DEFAULT_WINDOW_HEIGHT = 1080
    private const val DEFAULT_SELENIUM_GRID_URL = "http://localhost:4444/wd/hub"
    private val isRemoteExecution: Boolean by lazy { System.getProperty("remote", Configuration.isRemote).toBoolean() }
    private val seleniumGridUrl = System.getProperty("selenium.grid.url", DEFAULT_SELENIUM_GRID_URL)

    enum class Browser(val capabilities: () -> MutableCapabilities) {
        CHROME({
            ChromeOptions().apply {
                addArguments("--start-maximized", "--disable-extensions", "--incognito")
                setCapability("browserName", "chrome")
            }
        }),
        FIREFOX({
            FirefoxOptions().apply {
                addArguments("-private")
                setCapability("browserName", "firefox")
            }
        })
    }

    fun createDriver(
        browser: Browser,
        windowSize: Dimension = Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT)
    ): WebDriver {
        val remote = isRemoteExecution
        val driver = if (remote) {
            logger.info("Creating remote driver for browser: ${browser.name}")
            RemoteWebDriver(URL(seleniumGridUrl), browser.capabilities())
        } else {
            logger.info("Creating local driver for browser: ${browser.name}")
            when (browser) {
                Browser.CHROME -> {
                    WebDriverManager.chromedriver().setup()
                    ChromeDriver(browser.capabilities() as ChromeOptions)
                }
                Browser.FIREFOX -> {
                    WebDriverManager.firefoxdriver().setup()
                    FirefoxDriver(browser.capabilities() as FirefoxOptions)
                }
            }
        }.apply {
            manage().window().size = windowSize
        }

        driverThreadLocal.set(driver)
        logger.info("Created ${browser.name} driver (${if (remote) "remote" else "local"})")
        return driver
    }

    fun getDriver(): WebDriver {
        return driverThreadLocal.get() ?: throw IllegalStateException("WebDriver has not been initialized for this thread.")
    }

    fun quitDriver() {
        driverThreadLocal.get()?.let { driver ->
            try {
                driver.quit()
            } catch (e: Exception) {
                logger.error("Error quitting driver: ${e.message}")
            } finally {
                driverThreadLocal.remove()
            }
        }
    }
}