package com.webqa.core.driver

import com.webqa.core.config.Configuration
import org.openqa.selenium.Dimension
import org.openqa.selenium.Proxy
import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * Simple configuration for driver options.
 * Use named parameters or copy() for customization.
 */
data class DriverOptions(
    val headless: Boolean = false,
    val incognito: Boolean = true,
    val windowSize: Dimension = Dimension(1920, 1080),
    val maximized: Boolean = false,
    val implicitWait: Duration = Duration.ofSeconds(10),
    val pageLoadTimeout: Duration = Duration.ofSeconds(30),
    val scriptTimeout: Duration = Duration.ofSeconds(30),
    val proxy: Proxy? = null,
    val downloadDirectory: String? = null,
    val acceptInsecureCerts: Boolean = false,
    val additionalArguments: List<String> = emptyList(),
    val experimentalOptions: Map<String, Any> = emptyMap()
) {
    companion object {
        fun default() = DriverOptions()
        fun headless() = DriverOptions(headless = true)
    }
}

data class DriverConfiguration(
    val isRemote: Boolean,
    val gridUrl: String
)

object WebDriverFactory {
    private val logger = LoggerFactory.getLogger(WebDriverFactory::class.java)
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L
    private const val HEALTH_CHECK_TIMEOUT_MS = 5000

    private val config: DriverConfiguration by lazy {
        DriverConfiguration(
            isRemote = System.getProperty("remote")?.toBoolean() ?: Configuration.isRemote,
            gridUrl = System.getProperty("selenium.grid.url", Configuration.gridUrl)
        )
    }

    private val localFactory by lazy { LocalDriverFactory() }
    private val remoteFactory by lazy { RemoteDriverFactory(config.gridUrl) }

    fun createDriver(
        browser: BrowserCapabilities,
        options: DriverOptions = DriverOptions.default()
    ): WebDriver {
        logger.info(
            "Creating ${browser.browserName} driver " +
                "(mode: ${if (config.isRemote) "remote" else "local"}, headless: ${options.headless})"
        )

        return executeWithRetry("Create ${browser.browserName} driver") {
            if (config.isRemote) {
                ensureGridHealthy()
            }

            val factory = if (config.isRemote) remoteFactory else localFactory
            val driver = factory.createDriver(browser, options)

            initializeDriver(driver, options)
            DriverSession.set(driver)

            logger.info("Driver created successfully for thread ${Thread.currentThread().id}")
            driver
        }
    }

    fun createChromeDriver(options: DriverOptions = DriverOptions.default()): WebDriver =
        createDriver(BrowserCapabilities.Chrome, options)

    fun createFirefoxDriver(options: DriverOptions = DriverOptions.default()): WebDriver =
        createDriver(BrowserCapabilities.Firefox, options)

    fun createHeadlessChromeDriver(): WebDriver =
        createDriver(BrowserCapabilities.Chrome, DriverOptions.headless())

    fun getDriver(): WebDriver = DriverSession.get()
    fun getDriverOrNull(): WebDriver? = DriverSession.getOrNull()
    fun hasDriver(): Boolean = DriverSession.hasDriver()
    fun quitDriver() = DriverSession.quit()
    fun quitAllDrivers() = DriverSession.quitAll()
    fun getActiveDriverCount(): Int = DriverSession.getActiveDriverCount()
    fun getConfiguration(): DriverConfiguration = config

    private fun initializeDriver(driver: WebDriver, options: DriverOptions) {
        try {
            driver.manage().timeouts().apply {
                implicitlyWait(options.implicitWait)
                pageLoadTimeout(options.pageLoadTimeout)
                scriptTimeout(options.scriptTimeout)
            }

            when {
                options.maximized && !options.headless -> driver.manage().window().maximize()
                !options.maximized -> driver.manage().window().size = options.windowSize
            }

            logger.debug("Driver initialized with timeouts and window configuration")
        } catch (e: Exception) {
            logger.error("Failed to initialize driver: ${e.message}", e)
            runCatching { driver.quit() }
            throw DriverCreationException("Driver initialization failed", e)
        }
    }

    private fun ensureGridHealthy() {
        val statusUrl = config.gridUrl.replace("/wd/hub", "/status")
        try {
            val connection = (URL(statusUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS
                readTimeout = HEALTH_CHECK_TIMEOUT_MS
            }

            if (connection.responseCode != 200) {
                connection.disconnect()
                throw DriverCreationException("Selenium Grid unhealthy at $statusUrl")
            }
            connection.disconnect()
            logger.debug("Selenium Grid is healthy")
        } catch (e: DriverCreationException) {
            throw e
        } catch (e: Exception) {
            throw DriverCreationException("Cannot reach Selenium Grid at ${config.gridUrl}", e)
        }
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
            } catch (e: Exception) {
                logger.error("Non-retryable exception: ${e.message}")
                throw e
            }
        }

        throw lastException ?: RuntimeException("$operationName failed after $MAX_RETRY_ATTEMPTS attempts")
    }
}
