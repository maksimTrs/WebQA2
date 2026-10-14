package com.webqa.core.ui.pages

import com.webqa.core.ui.BasePage
import io.qameta.allure.Param
import io.qameta.allure.Step
import io.qameta.allure.model.Parameter
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

class LoginPage(driver: WebDriver) : BasePage(driver) {
    private companion object {
        val EMAIL_INPUT: By = By.id("email")
        val PASSWORD_INPUT: By = By.id("password")
        val LOGIN_BUTTON: By = By.cssSelector("button[type='submit']")
    }

    /** Submits the form; the caller waits for the outcome it expects (e.g. [HomePage.waitForLoginComplete]). */
    @Step("Log in as {email}")
    fun login(email: String, @Param(mode = Parameter.Mode.MASKED) password: String): HomePage {
        type(EMAIL_INPUT, "Email input", email)
        type(PASSWORD_INPUT, "Password input", password)
        click(LOGIN_BUTTON, "Login button")
        return HomePage(driver)
    }
}
