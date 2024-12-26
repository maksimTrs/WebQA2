package com.webqa.tests

import com.webqa.core.config.Configuration.App
import io.qameta.allure.Step
import io.qameta.allure.okhttp3.AllureOkHttp3
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.openapitools.client.apis.PetApi
import org.testng.annotations.BeforeClass
import java.util.concurrent.TimeUnit

private const val PET_SHOP_URL: String = "https://petstore3.swagger.io/api/v3"

abstract class BaseApiTest {
    protected val userEmail: String = App.userEmail
    protected val userPassword: String = App.userPass
    private lateinit var httpClient: OkHttpClient

    @BeforeClass
    fun setUpBase() {
        httpClient = createLoggingOkHttpClient()
    }

    @Step("Create HTTP client with logging and Allure interceptors")
    protected fun createLoggingOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val allureInterceptor = AllureOkHttp3()

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(allureInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Step("Create Pet API client")
    protected fun createPetApi(): PetApi {
        return PetApi(PET_SHOP_URL, httpClient)
    }
}
