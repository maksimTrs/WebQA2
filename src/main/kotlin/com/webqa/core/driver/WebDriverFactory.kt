import WebDriverFactory.Browser.CHROME
import WebDriverFactory.Browser.FIREFOX
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.RemoteWebDriver
import java.net.URL

object WebDriverFactory {
    enum class Browser { CHROME, FIREFOX }

    private val driverThreadLocal = ThreadLocal<WebDriver>()
    private const val SELENIUM_GRID_URL = "http://localhost:4444/wd/hub"

    @Synchronized
    fun createDriver(
        browser: Browser,
        windowSize: Dimension = Dimension(1920, 1080)
    ): WebDriver {
        quitDriver() // Ensure we don't have any leftover driver in this thread
        val driver = if (isRemoteExecution()) {
            createRemoteDriver(browser, windowSize)
        } else {
            when (browser) {
                CHROME -> createChromeDriver(windowSize)
                FIREFOX -> createFirefoxDriver(windowSize)
            }
        }
        driverThreadLocal.set(driver)
        println("Creating ${browser.name} driver (${if (isRemoteExecution()) "remote" else "local"})")
        return driver
    }

    @Synchronized
    fun quitDriver() {
        val driver = driverThreadLocal.get()
        try {
            driver?.quit()
        } catch (e: Exception) {
            println("Error quitting driver: ${e.message}")
        } finally {
            driverThreadLocal.remove()
        }
    }

    private fun createRemoteDriver(browser: Browser, windowSize: Dimension): WebDriver {
        println("Creating remote driver for browser: ${browser.name}")
        val capabilities = when (browser) {
            CHROME -> ChromeOptions().apply {
                addArguments("--start-maximized")
                addArguments("--disable-extensions")
                addArguments("--incognito")
                setCapability("browserName", "chrome")
            }

            FIREFOX -> FirefoxOptions().apply {
                addArguments("-private")
                setCapability("browserName", "firefox")
            }
        }
        return RemoteWebDriver(URL(SELENIUM_GRID_URL), capabilities).apply {
            manage().window().size = windowSize
        }
    }

    private fun createChromeDriver(windowSize: Dimension): WebDriver {
        val options = ChromeOptions().apply {
            addArguments("--start-maximized")
            addArguments("--disable-extensions")
            addArguments("--incognito")
        }

        return WebDriverManager.chromedriver().setup()
            .let { ChromeDriver(options).apply { manage().window().size = windowSize } }
    }

    private fun createFirefoxDriver(windowSize: Dimension): WebDriver {
        val options = FirefoxOptions().apply {
            addArguments("-private")
        }

        return WebDriverManager.firefoxdriver().setup()
            .let { FirefoxDriver(options).apply { manage().window().size = windowSize } }
    }

    private fun getBrowserFromConfig(): Browser {
        return when (System.getProperty("browser", "chrome").lowercase()) {
            "firefox" -> FIREFOX
            else -> CHROME
        }
    }

    private fun isRemoteExecution(): Boolean {
        return System.getProperty("remote", "false").toBoolean()
    }
}