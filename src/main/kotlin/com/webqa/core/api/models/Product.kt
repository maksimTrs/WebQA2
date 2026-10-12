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

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderResponse(
    val message: String,
    val transaction: Transaction
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Transaction(
    val id: Int,
    val order: Order
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Order(
    val id: String,
    val user: User,
    val products: List<ProductOrder>,
    val totalQuantity: Int,
    val status: String,
    val totalSum: BigDecimal
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
    val id: Int,
    val email: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProductOrder(
    val product: Product,
    val quantity: Int
)
