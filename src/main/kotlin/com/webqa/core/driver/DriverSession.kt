package com.webqa.core.driver

import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object DriverSession {
    private val logger = LoggerFactory.getLogger(DriverSession::class.java)
    private val driverThreadLocal = ThreadLocal<WebDriver>()

    // Concurrent set instead of a global lock: quit() is a network call and must not serialize teardowns
    private val activeDrivers: MutableSet<WebDriver> = ConcurrentHashMap.newKeySet()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown hook triggered - cleaning up active drivers")
            quitAll()
        })
    }

    fun set(driver: WebDriver) {
        driverThreadLocal.get()?.let { existing ->
            logger.warn("Replacing existing driver for thread ${Thread.currentThread().id}")
            activeDrivers.remove(existing)
            quitSafely(existing)
        }

        driverThreadLocal.set(driver)
        activeDrivers.add(driver)
        logger.debug("Driver set for thread ${Thread.currentThread().id}. Total active drivers: ${activeDrivers.size}")
    }

    fun get(): WebDriver {
        return driverThreadLocal.get()
            ?: throw IllegalStateException(
                "No WebDriver found for thread ${Thread.currentThread().id}. " +
                        "Call WebDriverFactory.createDriver() first."
            )
    }

    fun quit() {
        val driver = driverThreadLocal.get()
        if (driver == null) {
            logger.warn("No driver to quit for thread ${Thread.currentThread().id}")
            return
        }
        driverThreadLocal.remove()
        activeDrivers.remove(driver)
        quitSafely(driver)
        logger.info("Driver quit for thread ${Thread.currentThread().id}. Remaining active drivers: ${activeDrivers.size}")
    }

    private fun quitAll() {
        if (activeDrivers.isEmpty()) {
            logger.debug("No active drivers to quit")
            return
        }

        logger.info("Quitting ${activeDrivers.size} active driver(s)")
        activeDrivers.toList().forEach { driver ->
            activeDrivers.remove(driver)
            quitSafely(driver)
        }
        logger.info("All drivers quit successfully")
    }

    private fun quitSafely(driver: WebDriver) {
        try {
            driver.quit()
        } catch (e: Exception) {
            logger.error("Error quitting driver: ${e.message}", e)
        }
    }
}
