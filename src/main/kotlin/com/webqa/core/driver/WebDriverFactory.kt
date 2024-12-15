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
    private val SELENIUM_GRID_URL = System.getProperty("selenium.grid.url", "http://localhost:4444/wd/hub")

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

    @Synchronized
    fun createDriver(
        browser: Browser,
        windowSize: Dimension = Dimension(1920, 1080)
    ): WebDriver {
        quitDriver()
        val driver = if (isRemoteExecution()) {
            createRemoteDriver(browser, windowSize)
        } else {
            createLocalDriver(browser, windowSize)
        }
        driverThreadLocal.set(driver)
        logger.info("Created ${browser.name} driver (${if (isRemoteExecution()) "remote" else "local"})")
        return driver
    }

    @Synchronized
    fun quitDriver() {
        val driver = driverThreadLocal.get()
        try {
            driver?.quit()
        } catch (e: Exception) {
            logger.error("Error quitting driver: ${e.message}")
        } finally {
            driverThreadLocal.remove()
        }
    }

    private fun createRemoteDriver(browser: Browser, windowSize: Dimension): WebDriver {
        logger.info("Creating remote driver for browser: ${browser.name}")
        return RemoteWebDriver(URL(SELENIUM_GRID_URL), browser.capabilities()).apply {
            manage().window().size = windowSize
        }
    }

    private fun createLocalDriver(browser: Browser, windowSize: Dimension): WebDriver {
        return when (browser) {
            Browser.CHROME -> {
                WebDriverManager.chromedriver().setup()
                ChromeDriver(browser.capabilities() as ChromeOptions)
            }

            Browser.FIREFOX -> {
                WebDriverManager.firefoxdriver().setup()
                FirefoxDriver(browser.capabilities() as FirefoxOptions)
            }
        }.apply {
            manage().window().size = windowSize
        }
    }

    private fun isRemoteExecution(): Boolean {
        return System.getProperty("remote", "false").toBoolean()
    }
}