package com.webqa.core.driver

import com.webqa.core.config.Configuration
import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

private const val DEFAULT_WINDOW_WIDTH = 1920
private const val DEFAULT_WINDOW_HEIGHT = 1080
private const val DEFAULT_PAGE_TIMEOUT_SECONDS = 30L

/**
 * Simple configuration for driver options.
 * Use named parameters or copy() for customization.
 */
data class DriverOptions(
    val headless: Boolean = false,
    val incognito: Boolean = true,
    val windowSize: Dimension = Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT),
    val maximized: Boolean = false,
    val pageLoadTimeout: Duration = Duration.ofSeconds(DEFAULT_PAGE_TIMEOUT_SECONDS),
    val scriptTimeout: Duration = Duration.ofSeconds(DEFAULT_PAGE_TIMEOUT_SECONDS),
    val acceptInsecureCerts: Boolean = false,
    val additionalArguments: List<String> = emptyList()
)

object WebDriverFactory {
    private val logger = LoggerFactory.getLogger(WebDriverFactory::class.java)
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L
    private const val HEALTH_CHECK_TIMEOUT_MS = 5000

    private val isRemote: Boolean by lazy { System.getProperty("remote")?.toBoolean() ?: Configuration.isRemote }
    private val gridUrl: String by lazy { System.getProperty("selenium.grid.url", Configuration.gridUrl) }

    private val localFactory by lazy { LocalDriverFactory() }
    private val remoteFactory by lazy { RemoteDriverFactory(gridUrl) }

    fun createDriver(
        browser: BrowserCapabilities,
        options: DriverOptions = DriverOptions()
    ): WebDriver {
        logger.info(
            "Creating ${browser.browserName} driver " +
                "(mode: ${if (isRemote) "remote" else "local"}, headless: ${options.headless})"
        )

        return executeWithRetry("Create ${browser.browserName} driver") {
            if (isRemote) {
                ensureGridHealthy()
            }

            val factory = if (isRemote) remoteFactory else localFactory
            val driver = factory.createDriver(browser, options)

            initializeDriver(driver, options)
            DriverSession.set(driver)

            logger.info("Driver created successfully for thread ${Thread.currentThread().id}")
            driver
        }
    }

    fun getDriver(): WebDriver = DriverSession.get()
    fun quitDriver() = DriverSession.quit()

    private fun initializeDriver(driver: WebDriver, options: DriverOptions) {
        try {
            driver.manage().timeouts().apply {
                pageLoadTimeout(options.pageLoadTimeout)
                scriptTimeout(options.scriptTimeout)
            }

            when {
                options.maximized && !options.headless -> driver.manage().window().maximize()
                !options.maximized -> driver.manage().window().size = options.windowSize
            }

            logger.debug("Driver initialized with timeouts and window configuration")
        } catch (e: WebDriverException) {
            logger.error("Failed to initialize driver: ${e.message}", e)
            runCatching { driver.quit() }
            throw DriverCreationException("Driver initialization failed", e)
        }
    }

    private fun ensureGridHealthy() {
        val statusUrl = gridUrl.replace("/wd/hub", "/status")
        val responseCode = try {
            (URL(statusUrl).openConnection() as HttpURLConnection).run {
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS
                readTimeout = HEALTH_CHECK_TIMEOUT_MS
                try {
                    responseCode
                } finally {
                    disconnect()
                }
            }
        } catch (e: IOException) {
            throw DriverCreationException("Cannot reach Selenium Grid at $statusUrl", e)
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw DriverCreationException("Selenium Grid unhealthy at $statusUrl (HTTP $responseCode)")
        }
        logger.debug("Selenium Grid is healthy")
    }

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
