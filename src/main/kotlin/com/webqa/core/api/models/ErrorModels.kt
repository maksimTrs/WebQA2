package com.webqa.core.api.models

data class ErrorResponse(
    val error: ErrorDetails
)

data class ErrorDetails(
    val code: Int,
    val message: String,
    val fields: List<FieldError>? = null
)

data class FieldError(
    val message: String,
    val path: List<String>,
    val type: String
)
