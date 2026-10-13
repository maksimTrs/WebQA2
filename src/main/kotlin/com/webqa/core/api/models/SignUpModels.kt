package com.webqa.core.api.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class LoginRequest(
    val email: String,
    val password: String
)

data class SignUpRequest(
    val email: String,
    val password: String,
    val passwordConfirm: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SignUpResponse(
    val message: String? = null,
    val error: ErrorDetails? = null
)
