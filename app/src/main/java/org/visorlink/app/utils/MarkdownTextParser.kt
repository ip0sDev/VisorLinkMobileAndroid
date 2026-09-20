package org.visorlink.app.utils

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

/**
 * Парсер базового Markdown (bold, italic, strikethrough, monospace/code)
 * и ссылок/упоминаний для бабблов сообщений и списков диалогов.
 */
object MarkdownTextParser {

    private val MD_PATTERN = Pattern.compile("(\\*\\*([^*]+?)\\*\\*|\\*([^*]+?)\\*|~~([^~]+?)~~|`([^`]+?)`)")
    private val MENTION_REGEX = Regex("(?<!\\w)@[a-zA-Z0-9_]+")

    data class SpanRecord(val style: SpanStyle, val start: Int, val end: Int)

    fun parse(
        text: String,
        linkColor: Color? = null
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val plainBuilder = StringBuilder()
        val spans = mutableListOf<SpanRecord>()

        val matcher = MD_PATTERN.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            if (start > lastIndex) {
                plainBuilder.append(text.substring(lastIndex, start))
            }

            val fullMatch = matcher.group(1) ?: ""
            when {
                fullMatch.startsWith("**") && fullMatch.endsWith("**") && fullMatch.length >= 4 -> {
                    val content = fullMatch.substring(2, fullMatch.length - 2)
                    val spanStart = plainBuilder.length
                    plainBuilder.append(content)
                    spans.add(SpanRecord(SpanStyle(fontWeight = FontWeight.Bold), spanStart, plainBuilder.length))
                }
                fullMatch.startsWith("*") && fullMatch.endsWith("*") && fullMatch.length >= 2 -> {
                    val content = fullMatch.substring(1, fullMatch.length - 1)
                    val spanStart = plainBuilder.length
                    plainBuilder.append(content)
                    spans.add(SpanRecord(SpanStyle(fontStyle = FontStyle.Italic), spanStart, plainBuilder.length))
                }
                fullMatch.startsWith("~~") && fullMatch.endsWith("~~") && fullMatch.length >= 4 -> {
                    val content = fullMatch.substring(2, fullMatch.length - 2)
                    val spanStart = plainBuilder.length
                    plainBuilder.append(content)
                    spans.add(SpanRecord(SpanStyle(textDecoration = TextDecoration.LineThrough), spanStart, plainBuilder.length))
                }
                fullMatch.startsWith("`") && fullMatch.endsWith("`") && fullMatch.length >= 2 -> {
                    val content = fullMatch.substring(1, fullMatch.length - 1)
                    val spanStart = plainBuilder.length
                    plainBuilder.append(content)
                    spans.add(SpanRecord(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), spanStart, plainBuilder.length))
                }
                else -> {
                    plainBuilder.append(fullMatch)
                }
            }
            lastIndex = end
        }

        if (lastIndex < text.length) {
            plainBuilder.append(text.substring(lastIndex))
        }

        val plainText = plainBuilder.toString()

        return buildAnnotatedString {
            append(plainText)

            for (span in spans) {
                addStyle(span.style, span.start, span.end)
            }

            if (linkColor != null) {
                // URLs
                val urlMatcher = Patterns.WEB_URL.matcher(plainText)
                val urlRanges = mutableListOf<Pair<Int, Int>>()
                while (urlMatcher.find()) {
                    var url = urlMatcher.group() ?: continue
                    val uStart = urlMatcher.start()
                    val uEnd = urlMatcher.end()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), uStart, uEnd)
                    addStringAnnotation("URL", url, uStart, uEnd)
                    urlRanges.add(Pair(uStart, uEnd))
                }

                // Mentions
                MENTION_REGEX.findAll(plainText).forEach { match ->
                    val mStart = match.range.first
                    val mEnd = match.range.last + 1
                    val isInsideUrl = urlRanges.any { mStart >= it.first && mEnd <= it.second }
                    if (!isInsideUrl) {
                        addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), mStart, mEnd)
                        addStringAnnotation("MENTION", match.value, mStart, mEnd)
                    }
                }
            }
        }
    }

    /**
     * Очистка текста от Markdown-разметки для превью в списке чатов и темах.
     */
    fun stripMarkdown(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+?)\\*"), "$1")
            .replace(Regex("`([^`]+?)`"), "$1")
            .replace(Regex("~~([^~]+?)~~"), "$1")
    }
}
