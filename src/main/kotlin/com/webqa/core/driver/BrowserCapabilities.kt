package com.webqa.core.driver

import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions

/**
 * Type-safe browser capability configurations.
 * Each browser defines how to build its specific options based on DriverOptions.
 */
sealed interface BrowserCapabilities {
    val browserName: String

    /**
     * Builds browser-specific capabilities from common driver options.
     */
    fun buildCapabilities(options: DriverOptions): MutableCapabilities

    object Chrome : BrowserCapabilities {
        override val browserName = "chrome"

        override fun buildCapabilities(options: DriverOptions): ChromeOptions {
            return ChromeOptions().apply {
                // Base arguments
                val arguments = mutableListOf<String>()

                if (options.headless) {
                    arguments.add("--headless=new")
                }

                if (options.incognito) {
                    arguments.add("--incognito")
                }

                if (options.maximized && !options.headless) {
                    arguments.add("--start-maximized")
                }

                // Common stability arguments
                arguments.addAll(
                    listOf(
                        "--disable-extensions",
                        "--disable-gpu",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-blink-features=AutomationControlled"
                    )
                )

                // Additional custom arguments
                arguments.addAll(options.additionalArguments)

                addArguments(arguments)

                // Experimental options
                val prefs = mutableMapOf<String, Any>()

                options.downloadDirectory?.let {
                    prefs["download.default_directory"] = it
                    prefs["download.prompt_for_download"] = false
                }

                // Add custom experimental options
                options.experimentalOptions.forEach { (key, value) ->
                    setExperimentalOption(key, value)
                }

                if (prefs.isNotEmpty()) {
                    setExperimentalOption("prefs", prefs)
                }

                // Capabilities
                setAcceptInsecureCerts(options.acceptInsecureCerts)
                options.proxy?.let { setProxy(it) }
            }
        }
    }

    object Firefox : BrowserCapabilities {
        override val browserName = "firefox"

        override fun buildCapabilities(options: DriverOptions): FirefoxOptions {
            return FirefoxOptions().apply {
                // Base arguments
                val arguments = mutableListOf<String>()

                if (options.headless) {
                    arguments.add("-headless")
                }

                if (options.incognito) {
                    arguments.add("-private")
                }

                addArguments(arguments)

                // Additional custom arguments
                addArguments(options.additionalArguments)

                // Firefox preferences
                options.downloadDirectory?.let {
                    addPreference("browser.download.dir", it)
                    addPreference("browser.download.folderList", 2)
                    addPreference("browser.helperApps.neverAsk.saveToDisk", "application/octet-stream")
                }

                // Capabilities
                setAcceptInsecureCerts(options.acceptInsecureCerts)
                options.proxy?.let { setProxy(it) }
            }
        }
    }

    companion object {
        fun fromString(name: String): BrowserCapabilities {
            return when (name.lowercase()) {
                "chrome" -> Chrome
                "firefox" -> Firefox
                else -> throw IllegalArgumentException("Unsupported browser: $name. Supported: chrome, firefox")
            }
        }
    }
}