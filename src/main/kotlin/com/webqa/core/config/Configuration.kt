package com.webqa.core.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Configuration {
    private val config: Config = ConfigFactory.load()

    val browser: String = config.getString("webdriver.browser")
    val timeout: Int = config.getInt("webdriver.timeout")

    object App {
        val baseUrl: String = config.getString("app.baseUrl")
        val userEmail: String = config.getString("app.userEmail")
        val userPass: String = config.getString("app.password")
    }

    object Api {
        val baseUrl: String = config.getString("api.baseUrl")
        val timeout: Int = config.getInt("api.timeout")
    }
}
