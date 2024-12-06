package com.webqa.tests

import com.webqa.core.config.Configuration.App

abstract class BaseApiTest {

    companion object {
        @JvmStatic
        protected val USER_EMAIL = App.userEmail

        @JvmStatic
        protected val USER_PASSWORD = App.userPass
    }
}
