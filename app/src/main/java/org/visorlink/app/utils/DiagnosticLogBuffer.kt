package org.visorlink.app.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Кольцевой буфер диагностических логов в памяти для баг-репортов и отладки.
 * Хранит до 500 последних записей уровней I/W/E и неперехваченные сбои.
 * При фатальном падении сохраняет копию на диск (last_fatal_crash.txt).
 */
object DiagnosticLogBuffer {

    const val MAX_ENTRIES = 500
    const val MAX_OUTPUT_CHARS = 30000

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var isHandlerInstalled = false
    private var appContext: Context? = null

    fun install(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
        }
        if (isHandlerInstalled) return
        isHandlerInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record("FATAL", "CrashHandler", "Uncaught exception on thread ${thread.name}", throwable)
                appContext?.let { ctx ->
                    val file = File(ctx.filesDir, "last_fatal_crash.txt")
                    file.writeText(getFormattedLogs())
                }
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

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        record("I", tag, message)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        record("D", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        record("E", tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        record("W", tag, message, throwable)
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
        try {
            appContext?.let { File(it.filesDir, "last_fatal_crash.txt").delete() }
        } catch (_: Exception) {}
    }

    fun getFormattedLogs(): String {
        val raw = synchronized(lock) {
            buffer.joinToString(separator = "\n")
        }
        val crashFile = appContext?.let { File(it.filesDir, "last_fatal_crash.txt") }
        val crashLog = if (crashFile?.exists() == true) {
            try {
                "\n--- ПРЕДЫДУЩИЙ КРАШ ИЗ ФАЙЛА ---\n" + crashFile.readText() + "\n--------------------------------\n"
            } catch (_: Exception) { "" }
        } else ""

        val combined = if (crashLog.isNotEmpty()) crashLog + "\n" + raw else raw
        return if (combined.length > MAX_OUTPUT_CHARS) {
            combined.takeLast(MAX_OUTPUT_CHARS)
        } else {
            combined
        }
    }
}
