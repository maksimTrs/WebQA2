package com.webqa.core.ui.pages

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
        const val URL = "https://webqa.mercdev.com/signup"
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

    fun enterEmail(email: String) {
        emailInput.sendKeys(email)
    }

    fun enterPassword(password: String) {
        passwordInput.sendKeys(password)
    }

    fun enterConfirmPassword(password: String) {
        confirmPasswordInput.sendKeys(password)
    }

    fun isSignUpButtonEnabled(): Boolean {
        return signUpButton.isEnabled
    }

    fun clickSignUpButton() {
        WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.elementToBeClickable(signUpButton))
        signUpButton.click()
    }

    fun fillSignUpForm(email: String, password: String) {
        enterEmail(email)
        enterPassword(password)
        enterConfirmPassword(password)
    }

    fun isAlertPresent(): Boolean {
        return try {
            WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.alertIsPresent())
            true
        } catch (e: TimeoutException) {
            false
        }
    }

    fun getAlertText(): String {
        val alert = WebDriverWait(driver, Duration.ofSeconds(3))
            .until(ExpectedConditions.alertIsPresent())
        return alert.text
    }

    fun acceptAlert() {
        val alert = WebDriverWait(driver, Duration.ofSeconds(1))
            .until(ExpectedConditions.alertIsPresent())
        alert.accept()
    }
}