package com.webqa.core.driver

import org.slf4j.LoggerFactory
import java.time.Duration

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val delayBetweenAttempts: Duration = Duration.ofSeconds(2),
    val retryableExceptions: Set<Class<out Exception>> = setOf(
        DriverCreationException::class.java,
        DriverInitializationException::class.java
    )
) {
    companion object {
        val NO_RETRY = RetryPolicy(maxAttempts = 1, delayBetweenAttempts = Duration.ZERO)
        val DEFAULT = RetryPolicy()
        val AGGRESSIVE = RetryPolicy(maxAttempts = 5, delayBetweenAttempts = Duration.ofSeconds(3))
    }
}

object RetryExecutor {
    private val logger = LoggerFactory.getLogger(RetryExecutor::class.java)

    fun <T> executeWithRetry(
        policy: RetryPolicy,
        operationName: String,
        operation: () -> T
    ): T {
        var lastException: Exception? = null
        var attempt = 1

        while (attempt <= policy.maxAttempts) {
            try {
                if (attempt > 1) {
                    logger.info("Retry attempt $attempt of ${policy.maxAttempts} for: $operationName")
                }
                return operation()
            } catch (e: Exception) {
                lastException = e

                val isRetryable = policy.retryableExceptions.any { it.isInstance(e) }

                if (!isRetryable) {
                    logger.error("Non-retryable exception during $operationName: ${e.message}")
                    throw e
                }

                if (attempt < policy.maxAttempts) {
                    logger.warn(
                        "Attempt $attempt failed for $operationName: ${e.message}. " +
                                "Retrying in ${policy.delayBetweenAttempts.seconds}s..."
                    )
                    Thread.sleep(policy.delayBetweenAttempts.toMillis())
                    attempt++
                } else {
                    logger.error("All $attempt attempts failed for $operationName")
                    break
                }
            }
        }

        throw lastException
            ?: RuntimeException("Operation $operationName failed after ${policy.maxAttempts} attempts")
    }
}