package org.visorlink.app.ui.components.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import coil.compose.AsyncImage

@Composable
fun MarkdownText(text: String) {
    Column {
        val lines = text.split("\n")
        lines.forEach { line ->
            if (line.trim().startsWith("[img:") && line.trim().endsWith("]")) {
                val mediaId = line.trim().substringAfter("[img:").substringBefore("]")
                DiaryInlineImage(mediaId)
            } else if (line.trim().startsWith("- [") && line.contains("]")) {
                val isChecked = line.contains("- [x]", ignoreCase = true)
                val content = line.substringAfter("]").trim()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (isChecked) VlTheme.tokens.status.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else null
                    )
                }
            } else {
                val annotatedLine = buildAnnotatedString {
                    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
                    val italicRegex = Regex("_(.*?)_")
                    val colorRegex = Regex("\\{color:(#[0-9a-fA-F]{6})\\}(.*?)\\{/color\\}")
                    
                    var lastIndex = 0
                    val matches = (boldRegex.findAll(line).map { it to "bold" } +
                                   italicRegex.findAll(line).map { it to "italic" } +
                                   colorRegex.findAll(line).map { it to "color" })
                                  .sortedBy { it.first.range.first }

                    matches.forEach { (match, type) ->
                        if (match.range.first > lastIndex) {
                            append(line.substring(lastIndex, match.range.first))
                        }
                        when (type) {
                            "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
                            "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[1]) }
                            "color" -> {
                                val colorHex = match.groupValues[1]
                                val textContent = match.groupValues[2]
                                val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch(_:Exception) { Color.Unspecified }
                                withStyle(SpanStyle(color = color)) { append(textContent) }
                            }
                        }
                        lastIndex = match.range.last + 1
                    }
                    if (lastIndex < line.length) {
                        append(line.substring(lastIndex))
                    }
                }
                Text(
                    text = annotatedLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun DiaryInlineImage(mediaId: String) {
    val url = if (mediaId.startsWith("http")) mediaId else null
    
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .heightIn(max = 240.dp)
                .clip(VlTheme.tokens.shapes.chip),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, VlTheme.tokens.shapes.chip),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
