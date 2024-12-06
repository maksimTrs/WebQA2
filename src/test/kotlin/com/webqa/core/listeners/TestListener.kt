package com.webqa.core.listeners

import org.slf4j.LoggerFactory
import org.testng.ITestContext
import org.testng.ITestListener
import org.testng.ITestResult

class TestListener : ITestListener {
    private val logger = LoggerFactory.getLogger(TestListener::class.java)

    override fun onTestStart(result: ITestResult) {
        logger.info("Starting test: ${result.method.methodName}")
    }

    override fun onTestSuccess(result: ITestResult) {
        logger.info("Test passed: ${result.method.methodName}")
    }

    override fun onTestFailure(result: ITestResult) {
        logger.error("Test failed: ${result.method.methodName}")
        logger.error("Failure details: ${result.throwable.message}")
    }

    override fun onTestSkipped(result: ITestResult) {
        logger.warn("Test skipped: ${result.method.methodName}")
    }

    override fun onStart(context: ITestContext) {
        logger.info("Starting test suite: ${context.name}")
    }

    override fun onFinish(context: ITestContext) {
        logger.info("Finished test suite: ${context.name}")
        logger.info("Passed tests: ${context.passedTests.allResults.size}")
        logger.info("Failed tests: ${context.failedTests.allResults.size}")
        logger.info("Skipped tests: ${context.skippedTests.allResults.size}")
    }
}
