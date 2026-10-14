package com.webqa.core.api.clients

import com.webqa.core.config.Configuration
import com.webqa.petstore.apis.PetApi
import io.qameta.allure.okhttp3.AllureOkHttp3
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import java.time.Duration

/** Generated Petstore client over one shared OkHttpClient (one connection pool per run). */
object PetStoreClient {
    private val logger = LoggerFactory.getLogger(PetStoreClient::class.java)
    private val timeout: Duration = Duration.ofSeconds(Configuration.PetStore.timeout.toLong())

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        // Through SLF4J, not OkHttp's default java.util.logging sink; the full exchange is in Allure (AllureOkHttp3)
        .addInterceptor(
            HttpLoggingInterceptor { logger.info(it) }.apply { level = HttpLoggingInterceptor.Level.BASIC }
        )
        .addInterceptor(AllureOkHttp3())
        .connectTimeout(timeout)
        .readTimeout(timeout)
        .writeTimeout(timeout)
        .build()

    val petApi: PetApi = PetApi(Configuration.PetStore.baseUrl, httpClient)
}
