package org.visorlink.app.utils

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Кольцевой буфер диагностических логов в памяти для баг-репортов.
 * Хранит до 100 последних записей уровней W/E и неперехваченные сбои.
 */
object DiagnosticLogBuffer {

    private const val MAX_ENTRIES = 100
    private const val MAX_OUTPUT_CHARS = 5000

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var isHandlerInstalled = false

    fun install() {
        if (isHandlerInstalled) return
        isHandlerInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record("FATAL", "CrashHandler", "Uncaught exception on thread ${thread.name}", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun record(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val time = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        val stack = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "\n" + sw.toString().trim()
        } else ""

        val entry = "[$time] [$level/$tag]: $message$stack"

        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) {
                buffer.pollFirst()
            }
            buffer.addLast(entry)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        record("E", tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        record("W", tag, message, throwable)
    }

    fun getFormattedLogs(): String {
        val raw = synchronized(lock) {
            buffer.joinToString(separator = "\n")
        }
        return if (raw.length > MAX_OUTPUT_CHARS) {
            raw.takeLast(MAX_OUTPUT_CHARS)
        } else {
            raw
        }
    }
}
