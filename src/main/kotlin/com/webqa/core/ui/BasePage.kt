package com.webqa.core.ui

import com.webqa.core.config.Configuration
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.PageFactory
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

abstract class BasePage(protected val driver: WebDriver) {
    init {
        PageFactory.initElements(driver, this)
    }

    protected fun waitForElement(element: WebElement) {
        WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))
            .until(ExpectedConditions.visibilityOf(element))
    }

    protected fun waitForElementToBeClickable(element: WebElement) {
        WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))
            .until(ExpectedConditions.elementToBeClickable(element))
    }

    protected fun waitForElements(elements: List<WebElement>) {
        return WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))
            .until {
                (elements.all { it.isDisplayed })
            }
    }
}
