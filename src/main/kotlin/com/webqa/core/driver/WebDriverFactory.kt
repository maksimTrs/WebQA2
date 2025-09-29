package com.webqa.core.driver

import com.webqa.core.config.Configuration
import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

object WebDriverFactory {
    private val logger = LoggerFactory.getLogger(WebDriverFactory::class.java)

    private val config: DriverConfiguration by lazy {
        DriverConfiguration(
            isRemote = System.getProperty("remote", Configuration.isRemote).toBoolean(),
            gridUrl = System.getProperty("selenium.grid.url", "http://localhost:4444/wd/hub")
        )
    }

    private val localFactory by lazy { LocalDriverFactory() }
    private val remoteFactory by lazy { RemoteDriverFactory(config.gridUrl) }

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
            if (config.isRemote && performHealthCheck) {
                DriverHealthCheck.ensureGridHealthy(config.gridUrl)
            }

            val factory = if (config.isRemote) remoteFactory else localFactory

            val driver = factory.createDriver(browser, options)

            val initializer = DriverInitializer(options)
            val initializedDriver = initializer.initialize(driver)

            DriverSession.set(initializedDriver)

            logger.info("Driver creation completed successfully for thread ${Thread.currentThread().id}")
            initializedDriver
        }
    }

    fun createChromeDriver(options: DriverOptions = DriverOptions.default()): WebDriver {
        return createDriver(BrowserCapabilities.Chrome, options)
    }

    fun createFirefoxDriver(options: DriverOptions = DriverOptions.default()): WebDriver {
        return createDriver(BrowserCapabilities.Firefox, options)
    }

    fun createHeadlessChromeDriver(): WebDriver {
        return createDriver(BrowserCapabilities.Chrome, DriverOptions.headless())
    }

    fun getDriver(): WebDriver = DriverSession.get()

    fun getDriverOrNull(): WebDriver? = DriverSession.getOrNull()

    fun hasDriver(): Boolean = DriverSession.hasDriver()

    fun quitDriver() {
        DriverSession.quit()
    }

    fun quitAllDrivers() {
        DriverSession.quitAll()
    }

    fun getActiveDriverCount(): Int = DriverSession.getActiveDriverCount()

    fun getConfiguration(): DriverConfiguration = config
}

data class DriverConfiguration(
    val isRemote: Boolean,
    val gridUrl: String
)