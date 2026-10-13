package com.webqa.core.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Configuration {
    private val config: Config = ConfigFactory.load()

    val browser: String = config.getString("webdriver.browser")
    val headless: Boolean = config.getBoolean("webdriver.headless")
    val timeout: Int = config.getInt("webdriver.timeout")
    val gridUrl: String = config.getString("webdriver.gridUrl")
    val remote: Boolean = config.getBoolean("webdriver.remote")

    object App {
        val baseUrl: String = config.getString("app.baseUrl")
    }

    // Not in App: reading App.baseUrl (e.g. the sign-up page) must not require credentials
    object Credentials {
        val userEmail: String = requiredSecret("credentials.userEmail", "TEST_USER_EMAIL")
        val password: String = requiredSecret("credentials.password", "TEST_PASSWORD")
    }

    object Api {
        val baseUrl: String = config.getString("api.baseUrl")
        val timeout: Int = config.getInt("api.timeout")
    }

    object PetStore {
        val baseUrl: String = config.getString("petstore.baseUrl")
    }

    object Mock {
        object WireMock {
            val host: String = config.getString("mock.wiremock.host")
            val port: Int = config.getInt("mock.wiremock.port")
        }
    }

    private fun requiredSecret(path: String, envVar: String): String {
        val value = if (config.hasPath(path)) config.getString(path) else ""
        check(value.isNotBlank()) { "$envVar is not set: export it or add it to .env" }
        return value
    }
}
