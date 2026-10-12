package com.webqa.core.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Configuration {
    private val config: Config = ConfigFactory.load()

    val browser: String = config.getString("webdriver.browser")
    val headless: Boolean = config.getBoolean("webdriver.headless")
    val timeout: Int = config.getInt("webdriver.timeout")
    val isRemote: Boolean = config.getBoolean("webdriver.isRemote")
    val gridUrl: String = config.getString("webdriver.gridUrl")

    object App {
        val baseUrl: String = config.getString("app.baseUrl")
        val userEmail: String = requiredSecret("app.userEmail", "TEST_USER_EMAIL")
        val userPass: String = requiredSecret("app.password", "TEST_PASSWORD")
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

    private fun requiredSecret(path: String, envVar: String): String =
        config.takeIf { it.hasPath(path) }?.getString(path)?.takeIf { it.isNotBlank() }
            ?: error("$envVar is not set: export it or add it to .env")
}
