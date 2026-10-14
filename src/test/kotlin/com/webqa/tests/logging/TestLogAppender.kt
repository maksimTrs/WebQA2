package com.webqa.tests.logging

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase

/**
 * Collects the log lines of the test running on the current thread, for [TestLogListener] to attach to Allure.
 * Per thread: with parallel="methods" each test runs its configuration and test methods on one worker thread.
 * Events from threads without an open capture are ignored. Unsynchronized: the buffer is per thread,
 * so parallel tests need no shared lock.
 */
class TestLogAppender : UnsynchronizedAppenderBase<ILoggingEvent>() {
    /** Set from logback-test.xml. */
    var pattern: String = "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"

    private val layout = PatternLayout()

    override fun start() {
        layout.context = context
        layout.pattern = pattern
        layout.start()
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        val buffer = BUFFER.get() ?: return
        if (buffer.length < MAX_CHARS) {
            buffer.append(layout.doLayout(event))
        }
    }

    companion object {
        private const val MAX_CHARS = 200_000
        private val BUFFER = ThreadLocal<StringBuilder?>()

        /** Starts a capture on this thread unless one is open already. */
        fun begin() {
            if (BUFFER.get() == null) {
                BUFFER.set(StringBuilder())
            }
        }

        /** Ends the capture on this thread and returns what it collected, or null if none was open. */
        fun end(): String? = BUFFER.get()?.toString().also { BUFFER.remove() }
    }
}
