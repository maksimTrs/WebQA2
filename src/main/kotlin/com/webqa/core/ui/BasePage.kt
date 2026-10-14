package com.webqa.core.ui

import com.webqa.core.config.Configuration
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

/**
 * Elements are looked up by [By] on every action, not cached through PageFactory proxies:
 * a re-rendered React node is found again instead of failing with StaleElementReferenceException.
 *
 * Waits throw TimeoutException with a message that names what was expected, so a failed check
 * reports its cause instead of "expected true but was false".
 */
abstract class BasePage(protected val driver: WebDriver) {
    // Private: withMessage() mutates the wait, so every wait goes through waitUntil() and sets its own message
    private val wait = WebDriverWait(driver, Duration.ofSeconds(Configuration.timeout.toLong()))

    protected fun <T : Any> waitUntil(failureMessage: String, condition: ExpectedCondition<T>): T =
        wait.withMessage(failureMessage).until(condition)

    protected fun visible(locator: By, name: String): WebElement =
        waitUntil("$name is not visible ($locator)", ExpectedConditions.visibilityOfElementLocated(locator))

    protected fun allVisible(locator: By, name: String): List<WebElement> =
        waitUntil("$name are not visible ($locator)", ExpectedConditions.visibilityOfAllElementsLocatedBy(locator))

    protected fun click(locator: By, name: String) {
        waitUntil("$name is not clickable ($locator)", ExpectedConditions.elementToBeClickable(locator)).click()
    }

    protected fun type(locator: By, name: String, text: String) {
        visible(locator, name).sendKeys(text)
    }
}
