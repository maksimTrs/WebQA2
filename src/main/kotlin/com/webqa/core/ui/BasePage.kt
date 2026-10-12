package com.webqa.core.ui

import com.webqa.core.config.Configuration
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

abstract class BasePage(protected val driver: WebDriver) {
    protected val wait = WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))

    init {
        PageFactory.initElements(driver, this)
    }

    protected fun waitForElement(element: WebElement) {
        wait.until(ExpectedConditions.visibilityOf(element))
    }

    protected fun waitForElementToBeClickable(element: WebElement) {
        wait.until(ExpectedConditions.elementToBeClickable(element))
    }

    protected fun waitForElements(elements: List<WebElement>) {
        wait.until(ExpectedConditions.visibilityOfAllElements(elements))
    }
}
