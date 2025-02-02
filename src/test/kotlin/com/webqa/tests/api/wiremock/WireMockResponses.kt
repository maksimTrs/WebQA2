package com.webqa.tests.api.wiremock

object WireMockResponses {
    const val SUCCESS_RESPONSE = """
        {
            "message": "You have successfully registered"
        }
    """

    const val RATE_LIMIT_RESPONSE = """
        {
            "error": {
                "message": "Rate limit exceeded. Try again later."
            }
        }
    """

    fun emailExistsResponse(email: String) = """
        {
            "error": {
                "message": "Email $email already exists"
            }
        }
    """

    fun customErrorResponse(message: String) = """
        {
            "error": {
                "message": "$message"
            }
        }
    """
}
