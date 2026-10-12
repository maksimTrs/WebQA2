package com.webqa.core.api.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

// ignoreUnknown on every response model: the stand adds fields without notice (e.g. Order.createdAt)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Product(
    val id: String,
    val description: String,
    val name: String,
    val price: BigDecimal, // non-integer prices exist (e.g. 49.99)
    val imageUrl: String, // the stand also returns the legacy misspelled duplicate "iamgeUrl"
    val stockQuantity: Int,
    @JsonProperty("isOutOfStock")
    val isOutOfStock: Boolean
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProductResponse(
    val message: String,
    val products: List<Product>,
    val sale: Double
)
