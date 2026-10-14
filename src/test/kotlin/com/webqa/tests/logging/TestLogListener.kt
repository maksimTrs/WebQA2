package com.webqa.tests.logging

import io.qameta.allure.Allure
import org.slf4j.MDC
import org.testng.IInvokedMethod
import org.testng.IInvokedMethodListener
import org.testng.ITestResult

/**
 * Gives every test its own log: the test name goes to the MDC (the "test" key in the log patterns),
 * and the lines logged from its @BeforeMethod through the test method are attached to its Allure result
 * as "Test log" (INFO and up; build/logs/test.log has everything, DEBUG bodies included).
 * Registered through META-INF/services, so it is active in every run, with or without a suite file.
 */
class TestLogListener : IInvokedMethodListener {

    override fun beforeInvocation(method: IInvokedMethod, testResult: ITestResult) {
        if (method.isTestMethod || method.testMethod.isBeforeMethodConfiguration) {
            TestLogAppender.begin()
        }
        MDC.put(MDC_KEY, "${testResult.testClass.realClass.simpleName}.${method.testMethod.methodName}")
    }

    override fun afterInvocation(method: IInvokedMethod, testResult: ITestResult) {
        if (method.isTestMethod) {
            TestLogAppender.end()?.takeIf { it.isNotBlank() }?.let {
                Allure.addAttachment("Test log", "text/plain", it, "log")
            }
        } else if (method.testMethod.isBeforeMethodConfiguration && testResult.status == ITestResult.FAILURE) {
            // The test is skipped and Allure has already closed the fixture, so there is nothing to attach to:
            // the lines stay in build/logs/test.log under this method's name. Dropping the capture keeps
            // the next test on this thread from inheriting them
            TestLogAppender.end()
        }
        MDC.remove(MDC_KEY)
    }

    private companion object {
        const val MDC_KEY = "test"
    }
}
