package com.webqa.core.api.clients

import com.webqa.core.config.Configuration
import io.qameta.allure.okhttp3.AllureOkHttp3
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.openapitools.client.apis.PetApi
import java.time.Duration

/** Generated Petstore client over one shared OkHttpClient (one connection pool per run). */
object PetStoreClient {
    // Generous: the public demo is slow at times
    private const val TIMEOUT_SECONDS = 30L
    private val timeout: Duration = Duration.ofSeconds(TIMEOUT_SECONDS)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS })
        .addInterceptor(AllureOkHttp3())
        .connectTimeout(timeout)
        .readTimeout(timeout)
        .writeTimeout(timeout)
        .build()

    val petApi: PetApi = PetApi(Configuration.PetStore.baseUrl, httpClient)
}
