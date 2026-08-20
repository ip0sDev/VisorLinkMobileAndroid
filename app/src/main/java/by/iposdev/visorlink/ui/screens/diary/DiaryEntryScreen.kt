package by.iposdev.visorlink.ui.screens.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.diary.*
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryScreen(
    entryId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: DiaryViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    val initialText = remember(entryId, uiState.entries) {
        if (entryId != null) {
            uiState.entries.find { it.id == entryId }?.text ?: ""
        } else ""
    }

    var textFieldValue by remember(initialText) { mutableStateOf(TextFieldValue(initialText)) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDrawingDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadDiaryImage(uri) { mediaId ->
                textFieldValue = applyMarkdownInsert(textFieldValue, "\n[img:$mediaId]\n")
            }
        }
    }

    val visualTransformation = remember(primaryColor) { MarkdownWysiwygTransformation(primaryColor) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) stringResource(R.string.diary_new_entry) else stringResource(R.string.diary_edit_entry), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveEntry(textFieldValue.text) { success, _ ->
                            if (success) onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = VlTheme.tokens.shapes.cardRadius, topEnd = VlTheme.tokens.shapes.cardRadius)
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    AnimatedVisibility(
                        visible = showColorPicker,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val colors = listOf("#FF5252", "#FF4081", "#E040FB", "#7C4DFF", "#536DFE", "#448AFF", "#40C4FF", "#18FFFF", "#64FFDA", "#69F0AE", "#B2FF59", "#EEFF41", "#FFFF00", "#FFD740", "#FFAB40", "#FF6E40")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(colors) { hex ->
                                val c = try { Color(android.graphics.Color.parseColor(hex)) } catch(_:Exception) { Color.Unspecified }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(VlTheme.tokens.shapes.indicator)
                                        .background(c)
                                        .clickable {
                                            textFieldValue = toggleMarkdownTag(textFieldValue, "{color:$hex}", "{/color}")
                                            showColorPicker = false
                                        }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MarkdownToolButton(Icons.Default.FormatBold, "Bold") {
                            textFieldValue = toggleMarkdownTag(textFieldValue, "**")
                        }
                        MarkdownToolButton(Icons.Default.FormatItalic, "Italic") {
                            textFieldValue = toggleMarkdownTag(textFieldValue, "_")
                        }
                        MarkdownToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "List") {
                            textFieldValue = toggleCheckbox(textFieldValue, false)
                        }
                        MarkdownToolButton(Icons.Default.Checklist, "Checklist") {
                            textFieldValue = toggleCheckbox(textFieldValue, true)
                        }
                        MarkdownToolButton(Icons.Default.Image, "Image") {
                            imagePicker.launch("image/*")
                        }
                        MarkdownToolButton(Icons.Default.Brush, "Draw") {
                            showDrawingDialog = true
                        }
                        MarkdownToolButton(Icons.Default.Palette, "Color") {
                            showColorPicker = !showColorPicker
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            VlAmbientGlow()

            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation
            )

            LaunchedEffect(Unit) {
                delay(300)
                focusRequester.requestFocus()
            }
        }
    }

    if (showDrawingDialog) {
        VlDrawingDialog(
            onDismiss = { showDrawingDialog = false },
            onSave = { uri ->
                viewModel.uploadDiaryImage(uri) { mediaId ->
                    textFieldValue = applyMarkdownInsert(textFieldValue, "\n[img:$mediaId]\n")
                }
                showDrawingDialog = false
            }
        )
    }
}

fun toggleMarkdownTag(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val selectedText = text.substring(selection.start, selection.end)

    if (selection.start >= prefix.length && selection.end <= text.length - suffix.length) {
        val before = text.substring(selection.start - prefix.length, selection.start)
        val after = text.substring(selection.end, selection.end + suffix.length)
        if (before == prefix && after == suffix) {
            val newText = text.substring(0, selection.start - prefix.length) + selectedText + text.substring(selection.end + suffix.length)
            val newSelection = TextRange(selection.start - prefix.length, selection.end - prefix.length)
            return value.copy(text = newText, selection = newSelection)
        }
    }

    val newText = text.substring(0, selection.start) + prefix + selectedText + suffix + text.substring(selection.end)
    val newSelection = if (selectedText.isEmpty()) {
        TextRange(selection.start + prefix.length)
    } else {
        TextRange(selection.start, selection.end + prefix.length + suffix.length)
    }
    return value.copy(text = newText, selection = newSelection)
}

fun applyMarkdownInsert(value: TextFieldValue, insert: String): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val newText = text.substring(0, selection.start) + insert + text.substring(selection.end)
    return value.copy(text = newText, selection = TextRange(selection.start + insert.length))
}

fun toggleCheckbox(value: TextFieldValue, isChecklist: Boolean): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start

    var lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    if (lineStart < 0) lineStart = 0

    val line = text.substring(lineStart)
    val checkStr = if (isChecklist) "- [ ] " else "- "
    val doneCheckStr = "- [x] "
    val doneCheckStr2 = "- [X] "

    if (isChecklist) {
        if (line.startsWith(checkStr)) {
            val newText = text.substring(0, lineStart) + doneCheckStr + text.substring(lineStart + 6)
            return value.copy(text = newText)
        } else if (line.startsWith(doneCheckStr) || line.startsWith(doneCheckStr2)) {
            val newText = text.substring(0, lineStart) + text.substring(lineStart + 6)
            return value.copy(text = newText, selection = TextRange(maxOf(0, value.selection.start - 6)))
        } else if (line.startsWith("- ")) {
            val newText = text.substring(0, lineStart) + checkStr + text.substring(lineStart + 2)
            return value.copy(text = newText, selection = TextRange(value.selection.start + 4))
        } else {
            val newText = text.substring(0, lineStart) + checkStr + text.substring(lineStart)
            return value.copy(text = newText, selection = TextRange(value.selection.start + 6))
        }
    } else {
        if (line.startsWith("- ")) {
            val newText = text.substring(0, lineStart) + text.substring(lineStart + 2)
            return value.copy(text = newText, selection = TextRange(maxOf(0, value.selection.start - 2)))
        } else {
            val newText = text.substring(0, lineStart) + "- " + text.substring(lineStart)
            return value.copy(text = newText, selection = TextRange(value.selection.start + 2))
        }
    }
}

class MarkdownWysiwygTransformation(val primaryColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val content = text.text
        val builder = AnnotatedString.Builder()
        val origToTrans = IntArray(content.length + 1)
        val transToOrig = ArrayList<Int>()
        var transOffset = 0

        val imgRegex = Regex("\\[img:.*?\\]")
        val checkEmptyRegex = Regex("- \\[[ ]\\]")
        val checkCheckedRegex = Regex("- \\[x\\]|- \\[X\\]")
        val listRegex = Regex("^- ", RegexOption.MULTILINE)

        val replacements = mutableMapOf<Int, Pair<Int, String>>()
        imgRegex.findAll(content).forEach { replacements[it.range.first] = it.range.last + 1 to "🖼 Image" }
        checkEmptyRegex.findAll(content).forEach { replacements[it.range.first] = it.range.last + 1 to "☐ " }
        checkCheckedRegex.findAll(content).forEach { replacements[it.range.first] = it.range.last + 1 to "☑ " }
        listRegex.findAll(content).forEach { replacements[it.range.first] = it.range.last + 1 to "• " }

        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val italicRegex = Regex("_(.*?)_")
        val colorRegex = Regex("\\{color:(#[0-9a-fA-F]{6})\\}(.*?)\\{/color\\}")

        val hiddens = mutableSetOf<Int>()
        boldRegex.findAll(content).forEach {
            for(i in it.range.first until it.groups[1]!!.range.first) hiddens.add(i)
            for(i in it.groups[1]!!.range.last + 1 .. it.range.last) hiddens.add(i)
        }
        italicRegex.findAll(content).forEach {
            for(i in it.range.first until it.groups[1]!!.range.first) hiddens.add(i)
            for(i in it.groups[1]!!.range.last + 1 .. it.range.last) hiddens.add(i)
        }
        colorRegex.findAll(content).forEach {
            for(i in it.range.first until it.groups[2]!!.range.first) hiddens.add(i)
            for(i in it.groups[2]!!.range.last + 1 .. it.range.last) hiddens.add(i)
        }

        var origOffset = 0
        while (origOffset < content.length) {
            origToTrans[origOffset] = transOffset

            if (replacements.containsKey(origOffset)) {
                val (endOrig, replText) = replacements[origOffset]!!
                builder.append(replText)
                for (c in replText) {
                    transToOrig.add(origOffset)
                    transOffset++
                }
                while (origOffset < endOrig) {
                    origToTrans[origOffset] = transOffset
                    origOffset++
                }
                continue
            }

            if (hiddens.contains(origOffset)) {
                origOffset++
                continue
            }

            builder.append(content[origOffset])
            transToOrig.add(origOffset)
            transOffset++
            origOffset++
        }
        origToTrans[content.length] = transOffset
        transToOrig.add(content.length)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset < 0) return 0
                if (offset > content.length) return transOffset
                return origToTrans[offset]
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset < 0) return 0
                if (offset >= transToOrig.size) return content.length
                return transToOrig[offset]
            }
        }

        boldRegex.findAll(content).forEach { match ->
            val startTrans = origToTrans[match.groups[1]!!.range.first]
            val endTrans = origToTrans[match.groups[1]!!.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), startTrans, endTrans)
        }
        italicRegex.findAll(content).forEach { match ->
            val startTrans = origToTrans[match.groups[1]!!.range.first]
            val endTrans = origToTrans[match.groups[1]!!.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), startTrans, endTrans)
        }
        colorRegex.findAll(content).forEach { match ->
            val color = try { Color(android.graphics.Color.parseColor(match.groups[1]!!.value)) } catch(_:Exception) { Color.Unspecified }
            val startTrans = origToTrans[match.groups[2]!!.range.first]
            val endTrans = origToTrans[match.groups[2]!!.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(color = color), startTrans, endTrans)
        }
        imgRegex.findAll(content).forEach { match ->
            val startTrans = origToTrans[match.range.first]
            val endTrans = origToTrans[match.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), startTrans, endTrans)
        }
        checkEmptyRegex.findAll(content).forEach { match ->
            val startTrans = origToTrans[match.range.first]
            val endTrans = origToTrans[match.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(color = Color.Gray, fontSize = 20.sp), startTrans, endTrans)
        }
        checkCheckedRegex.findAll(content).forEach { match ->
            val startTrans = origToTrans[match.range.first]
            val endTrans = origToTrans[match.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(color = Color(0xFF10B981), fontSize = 20.sp), startTrans, endTrans)
        }
        listRegex.findAll(content).forEach { match ->
            val startTrans = origToTrans[match.range.first]
            val endTrans = origToTrans[match.range.last + 1]
            if (startTrans < endTrans) builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), startTrans, endTrans)
        }

        return TransformedText(builder.toAnnotatedString(), mapping)
    }
}
