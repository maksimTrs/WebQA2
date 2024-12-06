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

    fun login(email: String, password: String) {
        waitForElement(emailInput)
        emailInput.sendKeys(email)
        passwordInput.sendKeys(password)
        waitForElementToBeClickable(loginButton)
        loginButton.click()
    }
}
