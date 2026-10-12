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

                setAcceptInsecureCerts(options.acceptInsecureCerts)
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

                setAcceptInsecureCerts(options.acceptInsecureCerts)
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