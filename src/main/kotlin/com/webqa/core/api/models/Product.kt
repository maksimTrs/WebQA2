package com.webqa.core.api.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Product(
    val id: String,
    val description: String,
    val name: String,
    val price: Int,
    @JsonProperty("iamgeUrl")
    val imageUrl: String
)

data class ProductResponse(
    val message: String,
    val products: List<Product>,
    val sale: Double
)

data class ApiResponse(
    val message: String,
    val transaction: Transaction
)

data class Transaction(
    val id: Int,
    val order: Order
)

data class Order(
    val id: String,
    val user: User,
    val products: List<ProductOrder>,
    val totalQuantity: Int,
    val status: String,
    val totalSum: Int
)

data class User(
    val id: Int,
    val email: String
)

data class ProductOrder(
    val product: Product,
    val quantity: Int
)