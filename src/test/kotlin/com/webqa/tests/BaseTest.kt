package com.webqa.tests

import WebDriverFactory
import com.webqa.core.config.Configuration.App
import io.qameta.allure.Allure
import io.qameta.allure.Step
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.testng.ITestResult
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import java.io.ByteArrayInputStream

abstract class BaseTest {
    protected val baseUrl = App.baseUrl
    protected val userEmail = App.userEmail
    protected val userPass = App.userPass

    private lateinit var driver: WebDriver

    @BeforeMethod
    @Step("Initialize WebDriver")
    fun setUp() {
        driver = WebDriverFactory.createDriver()
    }

    @AfterMethod(alwaysRun = true)
    @Step("Close WebDriver")
    fun tearDown(testResult: ITestResult) {
        if (!::driver.isInitialized) {
            return
        }

        try {
            if (testResult.status == ITestResult.FAILURE) {
                attachScreenshot(testResult)
            }
        } catch (e: Exception) {
            println("Failed to capture failure evidence: ${e.message}")
        } finally {
            try {
                WebDriverFactory.quitDriver()
            } catch (e: Exception) {
                println("Failed to quit driver: ${e.message}")
            }
        }
    }

    private fun attachScreenshot(testResult: ITestResult) {
        try {
            val screenshot = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
            Allure.addAttachment(
                "Screenshot-${testResult.name}",
                ByteArrayInputStream(screenshot)
            )
        } catch (e: Exception) {
            println("Failed to capture screenshot for ${testResult.name}: ${e.message}")
        }
    }

    protected fun getDriver(): WebDriver = driver
}
