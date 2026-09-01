package by.iposdev.visorlink.ui.screens.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.ui.theme.VlTheme
import java.util.regex.Pattern

/**
 * Рендерер HTML-разметки для юридических документов VisorLink
 * согласно спецификации Biolume Legal & Compliance.
 */
@Composable
fun LegalHtmlContent(
    html: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(html) { parseHtmlBlocks(html) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.LeadParagraph -> {
                    Text(
                        text = rememberAnnotatedText(block.rawHtml),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                is HtmlBlock.Paragraph -> {
                    Text(
                        text = rememberAnnotatedText(block.rawHtml),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                is HtmlBlock.Callout -> {
                    val borderColor = when (block.type) {
                        CalloutType.WARNING -> VlTheme.tokens.status.warning.copy(alpha = 0.8f)
                        CalloutType.INFO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        CalloutType.CONTACTS -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    }
                    val bgContainer = MaterialTheme.colorScheme.surfaceContainer

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgContainer)
                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!block.title.isNullOrBlank()) {
                                Text(
                                    text = block.title,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            if (!block.content.isNullOrBlank()) {
                                Text(
                                    text = rememberAnnotatedText(block.content),
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
                is HtmlBlock.BulletList -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (item in block.items) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = rememberAnnotatedText(item),
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is HtmlBlock.RawText -> {
                    Text(
                        text = rememberAnnotatedText(block.text),
                        style = TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

/**
 * Парсер инлайнового HTML (<strong>, <b>, <a>, <span class="legal-badge">) в AnnotatedString.
 */
@Composable
fun rememberAnnotatedText(rawHtml: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    return remember(rawHtml, primaryColor, onSurface, onSurfaceVariant) {
        buildAnnotatedStringFromHtml(rawHtml, primaryColor, onSurface, onSurfaceVariant)
    }
}

fun buildAnnotatedStringFromHtml(
    html: String,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
): AnnotatedString {
    // Очистка HTML тегов и создание AnnotatedString со стилями
    val builder = AnnotatedString.Builder()
    
    // Заменяем <br/> на перенос строки
    val normalized = html
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace("&laquo;", "«")
        .replace("&raquo;", "»")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    // Регулярное выражение для поиска тегов
    val tagPattern = Pattern.compile("<(/?)(\\w+)([^>]*)>")
    val matcher = tagPattern.matcher(normalized)

    var currentIndex = 0
    val boldStack = mutableListOf<Int>()
    val linkStack = mutableListOf<Pair<Int, String>>()

    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        val isClosing = matcher.group(1) == "/"
        val tagName = matcher.group(2)?.lowercase().orEmpty()
        val attributes = matcher.group(3) ?: ""

        // Добавляем текст до тега
        if (start > currentIndex) {
            builder.append(normalized.substring(currentIndex, start))
        }
        currentIndex = end

        val currentBuilderPos = builder.length

        when (tagName) {
            "strong", "b" -> {
                if (isClosing) {
                    if (boldStack.isNotEmpty()) {
                        val openPos = boldStack.removeAt(boldStack.size - 1)
                        if (currentBuilderPos > openPos) {
                            builder.addStyle(
                                SpanStyle(fontWeight = FontWeight.Bold),
                                openPos,
                                currentBuilderPos
                            )
                        }
                    }
                } else {
                    boldStack.add(currentBuilderPos)
                }
            }
            "a" -> {
                if (isClosing) {
                    if (linkStack.isNotEmpty()) {
                        val (openPos, url) = linkStack.removeAt(linkStack.size - 1)
                        if (currentBuilderPos > openPos) {
                            builder.addStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline
                                ),
                                openPos,
                                currentBuilderPos
                            )
                            builder.addStringAnnotation(
                                tag = "URL",
                                annotation = url,
                                start = openPos,
                                end = currentBuilderPos
                            )
                        }
                    }
                } else {
                    val hrefMatcher = Pattern.compile("href=[\"']([^\"']*)[\"']").matcher(attributes)
                    val href = if (hrefMatcher.find()) hrefMatcher.group(1) else ""
                    linkStack.add(Pair(currentBuilderPos, href))
                }
            }
        }
    }

    if (currentIndex < normalized.length) {
        builder.append(normalized.substring(currentIndex))
    }

    // Закрываем оставшиеся незакрытые теги
    val finalPos = builder.length
    for (openPos in boldStack) {
        if (finalPos > openPos) {
            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), openPos, finalPos)
        }
    }
    for ((openPos, url) in linkStack) {
        if (finalPos > openPos) {
            builder.addStyle(
                SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline),
                openPos,
                finalPos
            )
            builder.addStringAnnotation("URL", url, openPos, finalPos)
        }
    }

    return builder.toAnnotatedString()
}

sealed class HtmlBlock {
    data class LeadParagraph(val rawHtml: String) : HtmlBlock()
    data class Paragraph(val rawHtml: String) : HtmlBlock()
    data class Callout(val type: CalloutType, val title: String?, val content: String?) : HtmlBlock()
    data class BulletList(val items: List<String>) : HtmlBlock()
    data class RawText(val text: String) : HtmlBlock()
}

enum class CalloutType {
    WARNING,
    INFO,
    CONTACTS
}

private fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val result = mutableListOf<HtmlBlock>()

    // Разбиваем HTML на верхнеуровневые блоки: <div class="legal-callout...">, <p...>, <ul...>
    val blockRegex = Regex("(<div class=\"legal-callout[^\"]*\">.*?</div>|<p[^>]*>.*?</p>|<ul class=\"legal-list\">.*?</ul>)", RegexOption.DOT_MATCHES_ALL)
    var lastIndex = 0

    val matches = blockRegex.findAll(html).toList()
    if (matches.isEmpty()) {
        val clean = html.trim()
        if (clean.isNotEmpty()) {
            result.add(HtmlBlock.RawText(clean))
        }
        return result
    }

    for (match in matches) {
        val blockStr = match.value.trim()

        when {
            blockStr.startsWith("<div class=\"legal-callout") -> {
                val type = when {
                    blockStr.contains("legal-callout-warning") -> CalloutType.WARNING
                    blockStr.contains("legal-callout-contacts") -> CalloutType.CONTACTS
                    else -> CalloutType.INFO
                }
                val titleMatch = Regex("<div class=\"legal-callout-title\">(.*?)</div>", RegexOption.DOT_MATCHES_ALL).find(blockStr)
                val title = titleMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()

                val textMatch = Regex("<p class=\"legal-callout-text\">(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(blockStr)
                val content = textMatch?.groupValues?.get(1)?.trim() ?: blockStr.replace(Regex("<[^>]+>"), "").trim()

                result.add(HtmlBlock.Callout(type, title, content))
            }
            blockStr.startsWith("<ul class=\"legal-list\"") -> {
                val liMatches = Regex("<li class=\"legal-li\">(.*?)</li>", RegexOption.DOT_MATCHES_ALL).findAll(blockStr)
                val items = liMatches.map { it.groupValues[1].trim() }.toList()
                if (items.isNotEmpty()) {
                    result.add(HtmlBlock.BulletList(items))
                }
            }
            blockStr.startsWith("<p class=\"legal-p lead\"") -> {
                val inner = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(blockStr)?.groupValues?.get(1) ?: blockStr
                result.add(HtmlBlock.LeadParagraph(inner))
            }
            blockStr.startsWith("<p") -> {
                val inner = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(blockStr)?.groupValues?.get(1) ?: blockStr
                result.add(HtmlBlock.Paragraph(inner))
            }
            else -> {
                result.add(HtmlBlock.RawText(blockStr))
            }
        }
    }

    return result
}
