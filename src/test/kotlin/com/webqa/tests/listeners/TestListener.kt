package com.webqa.tests.listeners

import org.slf4j.LoggerFactory
import org.testng.ITestContext
import org.testng.ITestListener
import org.testng.ITestResult

/**
 * Test lifecycle and per-<test> totals in the log file, next to the lines each test logged.
 * Registered through META-INF/services, so it is active with or without a suite file.
 */
class TestListener : ITestListener {
    private val logger = LoggerFactory.getLogger(TestListener::class.java)

    override fun onTestStart(result: ITestResult) {
        logger.info("Starting test: {}", result.name())
    }

    override fun onTestSuccess(result: ITestResult) {
        logger.info("Test passed: {}", result.name())
    }

    override fun onTestFailure(result: ITestResult) {
        logger.error("Test failed: {}: {}", result.name(), result.throwable?.toString())
    }

    override fun onTestSkipped(result: ITestResult) {
        logger.warn("Test skipped: {}", result.name())
    }

    override fun onStart(context: ITestContext) {
        logger.info("Starting <test> '{}'", context.name)
    }

    override fun onFinish(context: ITestContext) {
        logger.info(
            "Finished <test> '{}': {} passed, {} failed, {} skipped",
            context.name,
            context.passedTests.allResults.size,
            context.failedTests.allResults.size,
            context.skippedTests.allResults.size
        )
    }

    private fun ITestResult.name() = "${testClass.realClass.simpleName}.${method.methodName}"
}
