package com.webqa.core.driver

import com.webqa.core.config.Configuration
import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

/**
 * Facade for WebDriver creation with comprehensive configuration, retry logic, and session management.
 *
 * Example usage:
 * ```
 * // Simple usage with defaults
 * val driver = WebDriverFactory.createDriver(BrowserCapabilities.Chrome)
 *
 * // Advanced usage with custom options
 * val options = DriverOptions.builder()
 *     .headless(true)
 *     .windowSize(1600, 900)
 *     .implicitWait(Duration.ofSeconds(15))
 *     .downloadDirectory("/path/to/downloads")
 *     .build()
 *
 * val driver = WebDriverFactory.createDriver(
 *     browser = BrowserCapabilities.Chrome,
 *     options = options,
 *     retryPolicy = RetryPolicy.AGGRESSIVE
 * )
 *
 * // Retrieve driver for current thread
 * val driver = WebDriverFactory.getDriver()
 *
 * // Clean up
 * WebDriverFactory.quitDriver()
 * ```
 */
object WebDriverFactory {
    private val logger = LoggerFactory.getLogger(WebDriverFactory::class.java)

    // Configuration loaded from system properties
    private val config: DriverConfiguration by lazy {
        DriverConfiguration(
            isRemote = System.getProperty("remote", Configuration.isRemote).toBoolean(),
            gridUrl = System.getProperty("selenium.grid.url", "http://localhost:4444/wd/hub")
        )
    }

    // Factory instances
    private val localFactory by lazy { LocalDriverFactory() }
    private val remoteFactory by lazy { RemoteDriverFactory(config.gridUrl) }

    /**
     * Creates a WebDriver instance with the specified configuration.
     *
     * @param browser The browser to use (Chrome, Firefox)
     * @param options Driver configuration options
     * @param retryPolicy Retry policy for resilient driver creation
     * @param performHealthCheck Whether to check Selenium Grid health before remote creation
     * @return Initialized WebDriver instance
     * @throws DriverCreationException if driver creation fails after all retry attempts
     */
    fun createDriver(
        browser: BrowserCapabilities,
        options: DriverOptions = DriverOptions.default(),
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
        performHealthCheck: Boolean = true
    ): WebDriver {
        logger.info(
            "Creating ${browser.browserName} driver " +
                    "(mode: ${if (config.isRemote) "remote" else "local"}, " +
                    "headless: ${options.headless})"
        )

        return RetryExecutor.executeWithRetry(
            policy = retryPolicy,
            operationName = "Create ${browser.browserName} driver"
        ) {
            // Health check for remote execution
            if (config.isRemote && performHealthCheck) {
                DriverHealthCheck.ensureGridHealthy(config.gridUrl)
            }

            // Select appropriate factory
            val factory = if (config.isRemote) remoteFactory else localFactory

            // Create driver
            val driver = factory.createDriver(browser, options)

            // Initialize driver (timeouts, window management)
            val initializer = DriverInitializer(options)
            val initializedDriver = initializer.initialize(driver)

            // Store in session
            DriverSession.set(initializedDriver)

            logger.info("Driver creation completed successfully for thread ${Thread.currentThread().id}")
            initializedDriver
        }
    }

    /**
     * Convenience method to create Chrome driver with default options.
     */
    fun createChromeDriver(options: DriverOptions = DriverOptions.default()): WebDriver {
        return createDriver(BrowserCapabilities.Chrome, options)
    }

    /**
     * Convenience method to create Firefox driver with default options.
     */
    fun createFirefoxDriver(options: DriverOptions = DriverOptions.default()): WebDriver {
        return createDriver(BrowserCapabilities.Firefox, options)
    }

    /**
     * Convenience method to create headless Chrome driver.
     */
    fun createHeadlessChromeDriver(): WebDriver {
        return createDriver(BrowserCapabilities.Chrome, DriverOptions.headless())
    }

    /**
     * Retrieves the WebDriver instance for the current thread.
     * @throws IllegalStateException if no driver exists for the current thread
     */
    fun getDriver(): WebDriver = DriverSession.get()

    /**
     * Safely retrieves the driver for the current thread, or null if none exists.
     */
    fun getDriverOrNull(): WebDriver? = DriverSession.getOrNull()

    /**
     * Checks if a driver exists for the current thread.
     */
    fun hasDriver(): Boolean = DriverSession.hasDriver()

    /**
     * Quits and removes the driver for the current thread.
     */
    fun quitDriver() {
        DriverSession.quit()
    }

    /**
     * Quits all active drivers across all threads.
     * Useful for cleanup in test teardown or application shutdown.
     */
    fun quitAllDrivers() {
        DriverSession.quitAll()
    }

    /**
     * Returns the count of currently active drivers across all threads.
     */
    fun getActiveDriverCount(): Int = DriverSession.getActiveDriverCount()

    /**
     * Returns the current driver configuration.
     */
    fun getConfiguration(): DriverConfiguration = config
}

/**
 * Configuration for driver execution mode and Selenium Grid connection.
 */
data class DriverConfiguration(
    val isRemote: Boolean,
    val gridUrl: String
)