package com.webqa.core.ui.pages

import com.webqa.core.config.Configuration
import com.webqa.core.ui.BasePage
import io.qameta.allure.Allure
import io.qameta.allure.Step
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions

class SignUpPage(driver: WebDriver) : BasePage(driver) {
    private companion object {
        val URL = "${Configuration.App.baseUrl}/signup"
        val EMAIL_INPUT: By = By.id("email")
        val PASSWORD_INPUT: By = By.id("password")
        val CONFIRM_PASSWORD_INPUT: By = By.id("passwordConfirm")
        val SIGN_UP_BUTTON: By = By.cssSelector("button[type='submit']")
    }

    @Step("Open the sign-up page")
    fun open(): SignUpPage {
        driver.get(URL)
        visible(EMAIL_INPUT, "Email input")
        return this
    }

    // A lambda step keeps the password out of allure-results, as in LoginPage.login
    fun fillSignUpForm(email: String, password: String): SignUpPage {
        Allure.step(
            "Fill the sign-up form for $email",
            Allure.ThrowableRunnableVoid {
                type(EMAIL_INPUT, "Email input", email)
                type(PASSWORD_INPUT, "Password input", password)
                type(CONFIRM_PASSWORD_INPUT, "Confirm password input", password)
            }
        )
        return this
    }

    @Step("Check whether the sign-up button is enabled")
    fun isSignUpButtonEnabled(): Boolean = visible(SIGN_UP_BUTTON, "Sign-up button").isEnabled

    @Step("Submit the sign-up form")
    fun clickSignUpButton(): SignUpPage {
        click(SIGN_UP_BUTTON, "Sign-up button")
        return this
    }

    /** Waits for the browser alert the app shows after sign-up; fails with a timeout if none appears. */
    @Step("Read the sign-up result alert")
    fun getAlertText(): String =
        waitUntil("no alert after sign-up", ExpectedConditions.alertIsPresent()).text
}
