package by.iposdev.visorlink.ui.screens.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.CdnService
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryScreen(
    entryId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: DiaryViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val appTheme by themeViewModel.appTheme.collectAsState()
    val isExthru = appTheme.isExthruFamily
    val isForge = appTheme.name == "FORGE"

    val initialText = remember(entryId, uiState.entries) {
        if (entryId != null) {
            uiState.entries.find { it.id == entryId }?.text ?: ""
        } else ""
    }

    var textFieldValue by remember(initialText) { mutableStateOf(TextFieldValue(initialText)) }
    var isPreview by remember { mutableStateOf(false) }
    var showDrawingDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadDiaryImage(uri) { mediaId ->
                textFieldValue = insertInlineImage(textFieldValue, mediaId)
            }
        }
    }

    val visualTransformation = remember { MarkdownVisualTransformation() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) stringResource(R.string.diary_new_entry) else stringResource(R.string.diary_edit_entry), fontWeight = FontWeight.Bold, fontFamily = if(isForge) FontFamily.Monospace else null) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { isPreview = !isPreview }) {
                        Icon(if (isPreview) Icons.Default.Edit else Icons.Default.Visibility, stringResource(R.string.diary_toggle_preview))
                    }
                    IconButton(onClick = {
                        viewModel.saveEntry(textFieldValue.text) { success, _ ->
                            if (success) onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!isPreview) {
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if(isExthru) 0.3f else 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MarkdownToolButton(Icons.Default.FormatBold, stringResource(R.string.diary_format_bold)) {
                            textFieldValue = applyMarkdownTag(textFieldValue, "**")
                        }
                        MarkdownToolButton(Icons.Default.FormatItalic, stringResource(R.string.diary_format_italic)) {
                            textFieldValue = applyMarkdownTag(textFieldValue, "_")
                        }
                        MarkdownToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.diary_format_list)) {
                            textFieldValue = applyMarkdownTag(textFieldValue, "\n- ", "")
                        }
                        MarkdownToolButton(Icons.Default.Checklist, stringResource(R.string.diary_format_check)) {
                            textFieldValue = applyMarkdownTag(textFieldValue, "\n- [ ] ", "")
                        }
                        MarkdownToolButton(Icons.Default.Image, stringResource(R.string.diary_format_image)) {
                            imagePicker.launch("image/*")
                        }
                        MarkdownToolButton(Icons.Default.Brush, stringResource(R.string.diary_format_draw)) {
                            showDrawingDialog = true
                        }
                        MarkdownToolButton(Icons.Default.Palette, stringResource(R.string.diary_format_color)) {
                            showColorPicker = !showColorPicker
                        }
                    }
                    if (showColorPicker) {
                        val colors = listOf("#FF5252", "#FF4081", "#E040FB", "#7C4DFF", "#536DFE", "#448AFF", "#40C4FF", "#18FFFF", "#64FFDA", "#69F0AE", "#B2FF59", "#EEFF41", "#FFFF00", "#FFD740", "#FFAB40", "#FF6E40")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(colors) { hex ->
                                val c = try { Color(android.graphics.Color.parseColor(hex)) } catch(_:Exception) { Color.Unspecified }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .clickable {
                                            textFieldValue = applyMarkdownTag(textFieldValue, "{color:$hex}", "{/color}")
                                            showColorPicker = false
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isExthru) VlAmbientGlow(appTheme = appTheme)

            if (isPreview) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(textFieldValue.text)
                }
            } else {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    visualTransformation = visualTransformation,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontFamily = if(isForge) FontFamily.Monospace else null
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                stringResource(R.string.diary_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontFamily = if(isForge) FontFamily.Monospace else null
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }

    if (showDrawingDialog) {
        VlDrawingDialog(
            onDismiss = { showDrawingDialog = false },
            onSave = { uri ->
                showDrawingDialog = false
                viewModel.uploadDiaryImage(uri) { mediaId ->
                    textFieldValue = insertInlineImage(textFieldValue, mediaId)
                }
            }
        )
    }
}

@Composable
fun MarkdownToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
    }
}

fun applyMarkdownTag(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
    val selectedText = value.text.substring(value.selection.start, value.selection.end)
    val newText = value.text.replaceRange(
        value.selection.start,
        value.selection.end,
        "$prefix$selectedText$suffix"
    )
    val newSelection = if (selectedText.isEmpty()) {
        TextRange(value.selection.start + prefix.length)
    } else {
        TextRange(value.selection.start + prefix.length + selectedText.length + suffix.length)
    }
    return value.copy(text = newText, selection = newSelection)
}

fun insertInlineImage(value: TextFieldValue, mediaId: String): TextFieldValue {
    val tag = "\n[img:$mediaId]\n"
    val newText = value.text.replaceRange(value.selection.start, value.selection.end, tag)
    return value.copy(
        text = newText,
        selection = TextRange(value.selection.start + tag.length)
    )
}

class MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val content = text.text
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            
            // Markers to find and style
            val markerRegex = Regex("\\*\\*|_|\\{color:#[0-9a-fA-F]{6}\\}|\\{/color\\}|\\[img:.*?\\]|- \\[[ xX]\\]")
            val matches = markerRegex.findAll(content)

            matches.forEach { match ->
                // Append text before marker
                if (match.range.first > lastIndex) {
                    append(content.substring(lastIndex, match.range.first))
                }
                
                // Style the marker itself to be GONE (transparent and 0.sp)
                withStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp)) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            
            if (lastIndex < content.length) {
                append(content.substring(lastIndex))
            }

            // Apply styles to the WHOLE string based on tags (since markers are still there but invisible)
            
            // Bold
            Regex("\\*\\*(.*?)\\*\\*").findAll(content).forEach { match ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }
            
            // Italic
            Regex("_(.*?)_").findAll(content).forEach { match ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
            }
            
            // Color
            Regex("\\{color:(#[0-9a-fA-F]{6})\\}(.*?)\\{/color\\}").findAll(content).forEach { match ->
                val color = try { Color(android.graphics.Color.parseColor(match.groupValues[1])) } catch(_:Exception) { Color.Unspecified }
                addStyle(SpanStyle(color = color), match.groups[2]!!.range.first, match.groups[2]!!.range.last + 1)
            }
            
            // Checkboxes
            Regex("- \\[([ xX])\\]").findAll(content).forEach { match ->
                val isChecked = match.groupValues[1] != " "
                addStyle(
                    SpanStyle(
                        color = if(isChecked) Color(0xFF10B981) else Color.Gray,
                        fontWeight = FontWeight.Black
                    ), 
                    match.range.first, match.range.last + 1
                )
            }
        }
        
        // Offset mapping is IDENTITY because we didn't actually remove characters from the string, 
        // just made them invisible and 0-sized. Cursor will still "pass through" them.
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

@Composable
fun VlDrawingDialog(onDismiss: () -> Unit, onSave: (Uri) -> Unit) {
    var paths by remember { mutableStateOf(listOf<Pair<Path, Color>>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text(stringResource(R.string.diary_format_draw)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) } },
                    actions = {
                        IconButton(onClick = { paths = emptyList() }) { Icon(Icons.Default.Delete, null) }
                        IconButton(onClick = {
                            // Simulate saving
                            onSave(Uri.EMPTY)
                        }) { Icon(Icons.Default.Check, null) }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(Color.White)) {
                Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        },
                        onDrag = { change, _ ->
                            currentPath?.lineTo(change.position.x, change.position.y)
                            val path = currentPath
                            if (path != null) {
                                paths = paths.filter { it.first != path } + (path to Color.Black)
                            }
                        },
                        onDragEnd = {
                            currentPath = null
                        }
                    )
                }) {
                    paths.forEach { (path, color) ->
                        drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

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
                        tint = if (isChecked) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
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
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaId) {
        url = CdnService.getFileUrl(mediaId)
    }
    
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}
