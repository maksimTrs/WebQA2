package com.webqa.core.driver

import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import java.util.logging.Level

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
                // Console log for the failure attachments; Firefox (geckodriver) does not support the log endpoint
                setCapability(
                    ChromeOptions.LOGGING_PREFS,
                    LoggingPreferences().apply { enable(LogType.BROWSER, Level.ALL) }
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
