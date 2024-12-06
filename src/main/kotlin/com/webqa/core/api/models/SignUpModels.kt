package com.webqa.core.api.models

data class SignUpRequest(
    val email: String,
    val password: String,
    val passwordConfirm: String
)

data class SignUpResponse(
    val message: String? = null,
    val error: ErrorDetails? = null
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