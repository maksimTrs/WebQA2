package com.webqa.core.driver

import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

/**
 * Manages WebDriver lifecycle using ThreadLocal storage for thread-safe parallel execution.
 * Supports automatic cleanup via shutdown hooks.
 */
object DriverSession {
    private val logger = LoggerFactory.getLogger(DriverSession::class.java)
    private val driverThreadLocal = ThreadLocal<WebDriver>()
    private val activeDrivers = mutableSetOf<WebDriver>()
    private val lock = Any()

    init {
        // Register shutdown hook to cleanup any remaining drivers
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown hook triggered - cleaning up active drivers")
            quitAll()
        })
    }

    /**
     * Stores a WebDriver instance for the current thread.
     */
    fun set(driver: WebDriver) {
        synchronized(lock) {
            // Clean up existing driver if present
            driverThreadLocal.get()?.let { existing ->
                logger.warn("Replacing existing driver for thread ${Thread.currentThread().id}")
                quitSafely(existing)
                activeDrivers.remove(existing)
            }

            driverThreadLocal.set(driver)
            activeDrivers.add(driver)
            logger.debug("Driver set for thread ${Thread.currentThread().id}. Total active drivers: ${activeDrivers.size}")
        }
    }

    /**
     * Retrieves the WebDriver instance for the current thread.
     * @throws IllegalStateException if no driver exists for the current thread
     */
    fun get(): WebDriver {
        return driverThreadLocal.get()
            ?: throw IllegalStateException(
                "No WebDriver found for thread ${Thread.currentThread().id}. " +
                        "Call WebDriverFactory.createDriver() first."
            )
    }

    /**
     * Checks if a driver exists for the current thread.
     */
    fun hasDriver(): Boolean = driverThreadLocal.get() != null

    /**
     * Safely retrieves the driver for the current thread, or null if none exists.
     */
    fun getOrNull(): WebDriver? = driverThreadLocal.get()

    /**
     * Quits and removes the driver for the current thread.
     */
    fun quit() {
        synchronized(lock) {
            driverThreadLocal.get()?.let { driver ->
                quitSafely(driver)
                activeDrivers.remove(driver)
                driverThreadLocal.remove()
                logger.info("Driver quit for thread ${Thread.currentThread().id}. Remaining active drivers: ${activeDrivers.size}")
            } ?: logger.warn("No driver to quit for thread ${Thread.currentThread().id}")
        }
    }

    /**
     * Quits all active drivers across all threads.
     * Useful for cleanup in test teardown or application shutdown.
     */
    fun quitAll() {
        synchronized(lock) {
            if (activeDrivers.isEmpty()) {
                logger.debug("No active drivers to quit")
                return
            }

            logger.info("Quitting ${activeDrivers.size} active driver(s)")
            val drivers = activeDrivers.toList()
            drivers.forEach { driver ->
                quitSafely(driver)
            }
            activeDrivers.clear()
            driverThreadLocal.remove()
            logger.info("All drivers quit successfully")
        }
    }

    /**
     * Returns the count of currently active drivers.
     */
    fun getActiveDriverCount(): Int = synchronized(lock) { activeDrivers.size }

    /**
     * Safely quits a driver, catching and logging any exceptions.
     */
    private fun quitSafely(driver: WebDriver) {
        try {
            driver.quit()
        } catch (e: Exception) {
            logger.error("Error quitting driver: ${e.message}", e)
        }
    }
}