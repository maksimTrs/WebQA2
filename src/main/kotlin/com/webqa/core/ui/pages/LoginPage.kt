package com.webqa.core.ui.pages

import com.webqa.core.ui.BasePage
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy

class LoginPage(driver: WebDriver) : BasePage(driver) {
    @FindBy(id = "email")
    private lateinit var emailInput: WebElement

    @FindBy(id = "password")
    private lateinit var passwordInput: WebElement

    @FindBy(css = "button[type='submit']")
    private lateinit var loginButton: WebElement


    fun enterEmail(email: String) {
        waitForElement(emailInput)
        emailInput.sendKeys(email)
    }

    fun enterPassword(password: String) {
        waitForElement(passwordInput)
        passwordInput.sendKeys(password)
    }

    fun clickLoginButton() {
        waitForElementToBeClickable(loginButton)
        loginButton.click()
    }

    fun login(email: String, password: String) {
        enterEmail(email)
        enterPassword(password)
        clickLoginButton()
    }
}
