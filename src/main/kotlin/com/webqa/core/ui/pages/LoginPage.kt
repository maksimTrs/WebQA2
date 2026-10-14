package com.webqa.core.ui.pages

import com.webqa.core.ui.BasePage
import io.qameta.allure.Allure
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

class LoginPage(driver: WebDriver) : BasePage(driver) {
    private companion object {
        val EMAIL_INPUT: By = By.id("email")
        val PASSWORD_INPUT: By = By.id("password")
        val LOGIN_BUTTON: By = By.cssSelector("button[type='submit']")
    }

    /**
     * Submits the form; the caller waits for the outcome it expects (e.g. [HomePage.waitForLoginComplete]).
     * A lambda step, not @Step: @Step records every argument in allure-results, and Mode.MASKED hides
     * the password only in the generated report, not in the raw results uploaded by CI
     */
    fun login(email: String, password: String): HomePage {
        Allure.step(
            "Log in as $email",
            Allure.ThrowableRunnableVoid {
                type(EMAIL_INPUT, "Email input", email)
                type(PASSWORD_INPUT, "Password input", password)
                click(LOGIN_BUTTON, "Login button")
            }
        )
        return HomePage(driver)
    }
}
