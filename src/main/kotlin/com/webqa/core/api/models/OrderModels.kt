package com.webqa.core.api.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

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
