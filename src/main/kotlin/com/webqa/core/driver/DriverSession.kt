package com.webqa.core.driver

import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

object DriverSession {
    private val logger = LoggerFactory.getLogger(DriverSession::class.java)
    private val driverThreadLocal = ThreadLocal<WebDriver>()
    private val activeDrivers = mutableSetOf<WebDriver>()
    private val lock = Any()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown hook triggered - cleaning up active drivers")
            quitAll()
        })
    }

    fun set(driver: WebDriver) {
        synchronized(lock) {
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

    fun get(): WebDriver {
        return driverThreadLocal.get()
            ?: throw IllegalStateException(
                "No WebDriver found for thread ${Thread.currentThread().id}. " +
                        "Call WebDriverFactory.createDriver() first."
            )
    }

    fun hasDriver(): Boolean = driverThreadLocal.get() != null

    fun getOrNull(): WebDriver? = driverThreadLocal.get()

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

    fun getActiveDriverCount(): Int = synchronized(lock) { activeDrivers.size }

    private fun quitSafely(driver: WebDriver) {
        try {
            driver.quit()
        } catch (e: Exception) {
            logger.error("Error quitting driver: ${e.message}", e)
        }
    }
}