package com.webqa.core.driver

import com.webqa.core.config.Configuration
import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.slf4j.LoggerFactory
import java.time.Duration

private const val WINDOW_WIDTH = 1920
private const val WINDOW_HEIGHT = 1080
private const val PAGE_TIMEOUT_SECONDS = 30L

object WebDriverFactory {
    private val logger = LoggerFactory.getLogger(WebDriverFactory::class.java)
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L
    private val WINDOW_SIZE = Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
    private val PAGE_TIMEOUT: Duration = Duration.ofSeconds(PAGE_TIMEOUT_SECONDS)

    private val isRemote: Boolean get() = Configuration.remote

    private val localFactory by lazy { LocalDriverFactory() }
    private val remoteFactory by lazy { RemoteDriverFactory(Configuration.gridUrl) }

    fun createDriver(browser: BrowserCapabilities, headless: Boolean): WebDriver {
        logger.info(
            "Creating ${browser.browserName} driver " +
                "(mode: ${if (isRemote) "remote" else "local"}, headless: $headless)"
        )

        return executeWithRetry("Create ${browser.browserName} driver") {
            val factory = if (isRemote) remoteFactory else localFactory
            val driver = factory.createDriver(browser, headless)

            initializeDriver(driver)
            DriverSession.set(driver)

            logger.info("Driver created successfully for thread ${Thread.currentThread().id}")
            driver
        }
    }

    fun getDriver(): WebDriver = DriverSession.get()
    fun quitDriver() = DriverSession.quit()

    private fun initializeDriver(driver: WebDriver) {
        try {
            driver.manage().timeouts().apply {
                pageLoadTimeout(PAGE_TIMEOUT)
                scriptTimeout(PAGE_TIMEOUT)
            }
            // Fixed size, not maximize(): the same layout locally, headless and on Grid
            driver.manage().window().size = WINDOW_SIZE

            logger.debug("Driver initialized with timeouts and window configuration")
        } catch (e: WebDriverException) {
            logger.error("Failed to initialize driver: ${e.message}", e)
            runCatching { driver.quit() }
            throw DriverCreationException("Driver initialization failed", e)
        }
    }

    // Retries only DriverCreationException: Grid connection and initialization failures, which are often transient
    private fun <T> executeWithRetry(operationName: String, operation: () -> T): T {
        var lastException: Exception? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                if (attempt > 0) {
                    logger.info("Retry attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS for: $operationName")
                }
                return operation()
            } catch (e: DriverCreationException) {
                lastException = e
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    logger.warn("Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${RETRY_DELAY_MS}ms...")
                    Thread.sleep(RETRY_DELAY_MS)
                }
            }
        }

        throw lastException ?: RuntimeException("$operationName failed after $MAX_RETRY_ATTEMPTS attempts")
    }
}
