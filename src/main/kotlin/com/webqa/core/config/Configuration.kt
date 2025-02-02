package com.webqa.core.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Configuration {
    private val config: Config = ConfigFactory.load()

    val browser: String = config.getString("webdriver.browser")
    val timeout: Int = config.getInt("webdriver.timeout")
    val isRemote: String = config.getString("webdriver.isRemote")

    object App {
        val baseUrl: String = config.getString("app.baseUrl")
        val userEmail: String = config.getString("app.userEmail")
        val userPass: String = config.getString("app.password")
    }

    object Api {
        val baseUrl: String = config.getString("api.baseUrl")
        val timeout: Int = config.getInt("api.timeout")
    }

    object Mock {
        object WireMock {
            val host: String = config.getString("mock.wiremock.host")
            val port: Int = config.getInt("mock.wiremock.port")
            val mappingsDir: String = config.getString("mock.wiremock.mappingsDir")
            val filesDir: String = config.getString("mock.wiremock.filesDir")

            object Options {
                val verbose: Boolean = config.getBoolean("mock.wiremock.options.verbose")
                val globalResponseTemplating: Boolean = config.getBoolean("mock.wiremock.options.globalResponseTemplating")
                val localResponseTemplating: Boolean = config.getBoolean("mock.wiremock.options.localResponseTemplating")
            }
        }
    }
}
