package com.webqa.core.driver

import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions

sealed interface BrowserCapabilities {
    val browserName: String

    fun buildCapabilities(headless: Boolean): MutableCapabilities

    object Chrome : BrowserCapabilities {
        override val browserName = "chrome"

        override fun buildCapabilities(headless: Boolean): ChromeOptions {
            return ChromeOptions().apply {
                if (headless) {
                    addArguments("--headless=new")
                }
                addArguments(
                    "--incognito",
                    "--disable-extensions",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled"
                )
            }
        }
    }

    object Firefox : BrowserCapabilities {
        override val browserName = "firefox"

        override fun buildCapabilities(headless: Boolean): FirefoxOptions {
            return FirefoxOptions().apply {
                if (headless) {
                    addArguments("-headless")
                }
                addArguments("-private")
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
