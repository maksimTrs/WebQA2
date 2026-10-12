package com.webqa.core.api.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorResponse(
    val error: ErrorDetails
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorDetails(
    val code: Int,
    val message: String,
    val fields: List<FieldError>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FieldError(
    val message: String,
    val path: List<String>,
    val type: String
)
