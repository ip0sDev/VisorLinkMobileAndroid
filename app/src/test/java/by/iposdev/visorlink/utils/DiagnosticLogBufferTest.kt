package org.visorlink.app.utils

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLogBufferTest {

    @Before
    fun setUp() {
        DiagnosticLogBuffer.clear()
    }

    @Test
    fun testRecordAndRetrieveLogs() {
        DiagnosticLogBuffer.record("I", "TestTag", "Testing info log message")
        DiagnosticLogBuffer.record("E", "TestTag", "Testing error log message", RuntimeException("Crash simulation"))

        val logs = DiagnosticLogBuffer.getFormattedLogs()
        assertTrue(logs.contains("Testing info log message"))
        assertTrue(logs.contains("Testing error log message"))
        assertTrue(logs.contains("Crash simulation"))
        assertTrue(logs.length <= DiagnosticLogBuffer.MAX_OUTPUT_CHARS)
    }

    @Test
    fun testBufferCapacityLimit() {
        for (i in 1..150) {
            DiagnosticLogBuffer.record("D", "OverflowTest", "Message #$i with some padding content")
        }

        val logs = DiagnosticLogBuffer.getFormattedLogs()
        assertTrue(logs.length <= DiagnosticLogBuffer.MAX_OUTPUT_CHARS)
        assertTrue(logs.contains("Message #150"))
    }
}
