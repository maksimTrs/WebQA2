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
