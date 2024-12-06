package com.webqa.core.ui.pages

import com.webqa.core.ui.BasePage
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.FindBy
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

class HomePage(driver: WebDriver) : BasePage(driver) {
    @FindBy(css = "h6.MuiTypography-alignCenter")
    private lateinit var welcomeText: WebElement

    @FindBy(css = "a[href='/login']")
    private lateinit var loginButton: WebElement

    @FindBy(css = "a[href='/signup']")
    private lateinit var signupButton: WebElement

    @FindBy(xpath = "//div/a/following::h6[contains(text(), 'Welcome')]")
    private lateinit var loggedInUserInfo: WebElement

    /* private val productCards: List<WebElement>
         get() = driver.findElements(By.cssSelector(".MuiGrid-container.MuiGrid-spacing-xs-1 .MuiGrid-item"))*/
    private val productCards: List<WebElement> by lazy {
        WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(".MuiGrid-container.MuiGrid-spacing-xs-1 .MuiGrid-item")))
    }

    @FindBy(xpath = "//h6[text()='Chart']/../div/table")
    private lateinit var chartCart: WebElement

    @FindBy(xpath = "//h6[text()='Chart']/../div/table//input")
    private lateinit var chartCartInputValue: WebElement

    fun isWelcomeTextDisplayed(): Boolean = welcomeText.isDisplayed

    fun clickLogin() {
        waitForElementToBeClickable(loginButton)
        loginButton.click()
    }

    fun clickSignup() {
        waitForElementToBeClickable(signupButton)
        signupButton.click()
    }

    fun waitForLoginComplete(expectedEmail: String) {
        val wait = WebDriverWait(driver, Duration.ofSeconds(10))
        wait.until { driver ->
            try {
                val text = loggedInUserInfo.text.trim()
                text.contains(expectedEmail)
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getLoggedInUserEmail(): String {
        waitForElement(loggedInUserInfo)
        val text = loggedInUserInfo.text.trim()
        // Extract email from text, assuming it's the last part after any separators
        return text.substringAfter("|").trim()
    }

    fun getProductsCount(): Int {
        waitForElement(loggedInUserInfo)
        return productCards.size
    }

    fun addProductToCart(index: Int) {
        // Wait for products container and all product cards to be loaded
        waitForElements(productCards)

        val addToCartButton = productCards[index].findElement(By.cssSelector(".MuiButton-containedSecondary"))
        waitForElementToBeClickable(addToCartButton)
        addToCartButton.click()
    }

    fun isChartCartDisplayed(): Boolean {
        waitForElement(chartCart)
        return chartCart.isDisplayed
    }

    fun getChartCartInputValue(): Int {
        waitForElement(chartCart)
        return chartCartInputValue.getAttribute("value").toInt()
    }
}
