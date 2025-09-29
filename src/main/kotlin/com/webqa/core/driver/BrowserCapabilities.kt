package com.webqa.core.driver

import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions

sealed interface BrowserCapabilities {
    val browserName: String

    fun buildCapabilities(options: DriverOptions): MutableCapabilities

    object Chrome : BrowserCapabilities {
        override val browserName = "chrome"

        override fun buildCapabilities(options: DriverOptions): ChromeOptions {
            return ChromeOptions().apply {
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

                arguments.addAll(
                    listOf(
                        "--disable-extensions",
                        "--disable-gpu",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-blink-features=AutomationControlled"
                    )
                )

                arguments.addAll(options.additionalArguments)

                addArguments(arguments)

                val prefs = mutableMapOf<String, Any>()

                options.downloadDirectory?.let {
                    prefs["download.default_directory"] = it
                    prefs["download.prompt_for_download"] = false
                }

                options.experimentalOptions.forEach { (key, value) ->
                    setExperimentalOption(key, value)
                }

                if (prefs.isNotEmpty()) {
                    setExperimentalOption("prefs", prefs)
                }

                setAcceptInsecureCerts(options.acceptInsecureCerts)
                options.proxy?.let { setProxy(it) }
            }
        }
    }

    object Firefox : BrowserCapabilities {
        override val browserName = "firefox"

        override fun buildCapabilities(options: DriverOptions): FirefoxOptions {
            return FirefoxOptions().apply {
                val arguments = mutableListOf<String>()

                if (options.headless) {
                    arguments.add("-headless")
                }

                if (options.incognito) {
                    arguments.add("-private")
                }

                addArguments(arguments)

                addArguments(options.additionalArguments)

                options.downloadDirectory?.let {
                    addPreference("browser.download.dir", it)
                    addPreference("browser.download.folderList", 2)
                    addPreference("browser.helperApps.neverAsk.saveToDisk", "application/octet-stream")
                }

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