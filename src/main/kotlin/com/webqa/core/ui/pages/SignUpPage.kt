package com.webqa.core.ui.pages

import com.webqa.core.config.Configuration
import com.webqa.core.ui.BasePage
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

class SignUpPage(driver: WebDriver) : BasePage(driver) {
    companion object {
        private var URL = "${Configuration.App.baseUrl}/signup"
    }

    @FindBy(id = "email")
    private lateinit var emailInput: WebElement

    @FindBy(id = "password")
    private lateinit var passwordInput: WebElement

    @FindBy(id = "passwordConfirm")
    private lateinit var confirmPasswordInput: WebElement

    @FindBy(css = "button[type='submit']")
    private lateinit var signUpButton: WebElement


    fun open() {
        driver.get(URL)
    }

    private fun enterEmail(email: String) {
        waitForElement(emailInput)
        emailInput.sendKeys(email)
    }

    private fun enterPassword(password: String) {
        waitForElement(passwordInput)
        passwordInput.sendKeys(password)
    }

    private fun enterConfirmPassword(password: String) {
        waitForElement(confirmPasswordInput)
        confirmPasswordInput.sendKeys(password)
    }

    fun isSignUpButtonEnabled(): Boolean {
        waitForElement(signUpButton)
        return signUpButton.isEnabled
    }

    fun clickSignUpButton() {
        waitForElementToBeClickable(signUpButton)
        signUpButton.click()
    }

    fun fillSignUpForm(email: String, password: String) {
        enterEmail(email)
        enterPassword(password)
        enterConfirmPassword(password)
    }

    fun isAlertPresent(): Boolean {
        return try {
            WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))
                .until(ExpectedConditions.alertIsPresent())
            true
        } catch (e: TimeoutException) {
            false
        }
    }

    fun getAlertText(): String {
        val alert = WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))
            .until(ExpectedConditions.alertIsPresent())
        return alert.text
    }

    fun acceptAlert() {
        val alert = WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))
            .until(ExpectedConditions.alertIsPresent())
        alert.accept()
    }
}