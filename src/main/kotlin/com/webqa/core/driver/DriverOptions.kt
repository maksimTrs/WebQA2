package com.webqa.core.driver

import org.openqa.selenium.Dimension
import org.openqa.selenium.Proxy
import java.time.Duration

/**
 * Comprehensive configuration options for WebDriver creation and initialization.
 */
data class DriverOptions(
    val headless: Boolean = false,
    val incognito: Boolean = true,
    val windowSize: Dimension = Dimension(1920, 1080),
    val maximized: Boolean = false,
    val implicitWait: Duration = Duration.ofSeconds(10),
    val pageLoadTimeout: Duration = Duration.ofSeconds(30),
    val scriptTimeout: Duration = Duration.ofSeconds(30),
    val proxy: Proxy? = null,
    val downloadDirectory: String? = null,
    val acceptInsecureCerts: Boolean = false,
    val additionalArguments: List<String> = emptyList(),
    val experimentalOptions: Map<String, Any> = emptyMap()
) {
    class Builder {
        private var headless: Boolean = false
        private var incognito: Boolean = true
        private var windowSize: Dimension = Dimension(1920, 1080)
        private var maximized: Boolean = false
        private var implicitWait: Duration = Duration.ofSeconds(10)
        private var pageLoadTimeout: Duration = Duration.ofSeconds(30)
        private var scriptTimeout: Duration = Duration.ofSeconds(30)
        private var proxy: Proxy? = null
        private var downloadDirectory: String? = null
        private var acceptInsecureCerts: Boolean = false
        private var additionalArguments: MutableList<String> = mutableListOf()
        private var experimentalOptions: MutableMap<String, Any> = mutableMapOf()

        fun headless(value: Boolean) = apply { this.headless = value }
        fun incognito(value: Boolean) = apply { this.incognito = value }
        fun windowSize(width: Int, height: Int) = apply { this.windowSize = Dimension(width, height) }
        fun windowSize(dimension: Dimension) = apply { this.windowSize = dimension }
        fun maximized(value: Boolean) = apply { this.maximized = value }
        fun implicitWait(duration: Duration) = apply { this.implicitWait = duration }
        fun pageLoadTimeout(duration: Duration) = apply { this.pageLoadTimeout = duration }
        fun scriptTimeout(duration: Duration) = apply { this.scriptTimeout = duration }
        fun proxy(proxy: Proxy) = apply { this.proxy = proxy }
        fun downloadDirectory(path: String) = apply { this.downloadDirectory = path }
        fun acceptInsecureCerts(value: Boolean) = apply { this.acceptInsecureCerts = value }
        fun addArgument(argument: String) = apply { this.additionalArguments.add(argument) }
        fun addArguments(vararg arguments: String) = apply { this.additionalArguments.addAll(arguments) }
        fun addExperimentalOption(key: String, value: Any) = apply { this.experimentalOptions[key] = value }

        fun build() = DriverOptions(
            headless = headless,
            incognito = incognito,
            windowSize = windowSize,
            maximized = maximized,
            implicitWait = implicitWait,
            pageLoadTimeout = pageLoadTimeout,
            scriptTimeout = scriptTimeout,
            proxy = proxy,
            downloadDirectory = downloadDirectory,
            acceptInsecureCerts = acceptInsecureCerts,
            additionalArguments = additionalArguments.toList(),
            experimentalOptions = experimentalOptions.toMap()
        )
    }

    companion object {
        fun builder() = Builder()

        fun default() = DriverOptions()

        fun headless() = Builder()
            .headless(true)
            .build()
    }
}