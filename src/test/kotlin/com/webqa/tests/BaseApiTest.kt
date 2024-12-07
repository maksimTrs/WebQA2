package com.webqa.tests

import com.webqa.core.config.Configuration.App
import io.qameta.allure.okhttp3.AllureOkHttp3
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.openapitools.client.apis.PetApi

abstract class BaseApiTest {

    protected val userEmail: String = App.userEmail
    protected val userPassword: String = App.userPass
    protected val petURL: String = "https://petstore3.swagger.io/api/v3"


    protected fun createLoggingOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val allureInterceptor = AllureOkHttp3()

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(allureInterceptor)
            .build()
    }

    protected fun createPetApi(): PetApi {
        val client = createLoggingOkHttpClient()
        return PetApi(petURL, client)
    }
}
