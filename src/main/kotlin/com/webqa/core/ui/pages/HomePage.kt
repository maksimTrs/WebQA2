package com.webqa.core.ui.pages

import com.webqa.core.config.Configuration
import com.webqa.core.ui.BasePage
import io.qameta.allure.Step
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions

class HomePage(driver: WebDriver) : BasePage(driver) {
    private companion object {
        val LOGIN_LINK: By = By.cssSelector("a[href='/login']")
        val LOGGED_IN_USER_INFO: By = By.xpath("//div/a/following::h6[contains(text(), 'Welcome')]")
        val PRODUCT_CARDS: By = By.cssSelector(".MuiGrid-container.MuiGrid-spacing-xs-1 .MuiGrid-item")
        val ADD_TO_CART_BUTTON: By = By.cssSelector(".MuiButton-containedSecondary")

        // The app labels its cart "Chart"
        val CART: By = By.xpath("//h6[text()='Chart']/../div/table")
        val CART_QUANTITY_INPUT: By = By.xpath("//h6[text()='Chart']/../div/table//input")
    }

    @Step("Open the home page")
    fun open(): HomePage {
        driver.get(Configuration.App.baseUrl)
        visible(LOGIN_LINK, "Login link")
        return this
    }

    @Step("Go to the login page")
    fun clickLogin(): LoginPage {
        click(LOGIN_LINK, "Login link")
        return LoginPage(driver)
    }

    @Step("Wait until {expectedEmail} is shown as logged in")
    fun waitForLoginComplete(expectedEmail: String): HomePage {
        waitUntil(
            "logged-in user '$expectedEmail' is not shown in the header",
            ExpectedConditions.textToBePresentInElementLocated(LOGGED_IN_USER_INFO, expectedEmail)
        )
        return this
    }

    @Step("Read the logged-in user email")
    fun getLoggedInUserEmail(): String =
        // The email follows the "|" separator in the greeting
        visible(LOGGED_IN_USER_INFO, "Logged-in user info").text.trim().substringAfter("|").trim()

    @Step("Count the displayed products")
    fun getProductsCount(): Int =
        waitUntil("no product cards ($PRODUCT_CARDS)", ExpectedConditions.presenceOfAllElementsLocatedBy(PRODUCT_CARDS))
            .size

    @Step("Add product #{index} to the cart")
    fun addProductToCart(index: Int): HomePage {
        val cards = allVisible(PRODUCT_CARDS, "Product cards")
        check(index in cards.indices) { "No product card #$index: ${cards.size} displayed" }
        click("'Add to cart' of product #$index is not clickable") {
            it.findElements(PRODUCT_CARDS).getOrNull(index)?.findElement(ADD_TO_CART_BUTTON)
        }
        return this
    }

    /** Waits for the cart to appear; fails with a timeout naming the cart if it does not. */
    @Step("Read the cart quantity")
    fun getCartQuantity(): Int {
        visible(CART, "Cart")
        val input = waitUntil(
            "Cart quantity input is missing ($CART_QUANTITY_INPUT)",
            ExpectedConditions.presenceOfElementLocated(CART_QUANTITY_INPUT)
        )
        val value = input.getDomProperty("value")
        return checkNotNull(value) { "Cart quantity input has no value" }.toInt()
    }
}
