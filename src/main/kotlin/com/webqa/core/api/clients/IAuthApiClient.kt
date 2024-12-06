package com.webqa.core.api.clients

import com.webqa.core.api.models.SignUpRequest

interface IAuthApiClient<L, S> {
    fun login(email: String, password: String): L
    fun signup(request: SignUpRequest, statusCodeVal: Int): S
}