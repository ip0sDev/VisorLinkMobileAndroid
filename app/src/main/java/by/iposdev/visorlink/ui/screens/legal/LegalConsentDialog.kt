package by.iposdev.visorlink.ui.screens.legal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.data.model.LegalDocument
import by.iposdev.visorlink.data.model.LegalSection
import by.iposdev.visorlink.data.repository.LegalRepository
import by.iposdev.visorlink.ui.components.VlButton
import by.iposdev.visorlink.ui.components.VlSegmentedControl
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.theme.VlDepth
import by.iposdev.visorlink.ui.theme.VlTheme
import kotlinx.coroutines.launch

/**
 * Неотклоняемый полноэкранный диалог согласия с юридическими документами
 * с поддержкой Biolume/Abyss/Tidepool и версионирования.
 */
@Composable
fun LegalConsentDialog(
    currentVersion: String,
    legalRepository: LegalRepository,
    isReadOnly: Boolean = false,
    onAccept: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDismissReadOnly: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: ToS, 1: Privacy
    var isAgreed by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    var tosDoc by remember { mutableStateOf<LegalDocument?>(null) }
    var privacyDoc by remember { mutableStateOf<LegalDocument?>(null) }
    var isLoadingDocs by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    // Загрузка документов (с фоллбэком на bundled assets)
    LaunchedEffect(Unit) {
        isLoadingDocs = true
        try {
            val loadedTos = legalRepository.getTosDocument()
            val loadedPrivacy = legalRepository.getPrivacyPolicyDocument()
            tosDoc = loadedTos
            privacyDoc = loadedPrivacy
        } catch (e: Exception) {
            tosDoc = legalRepository.loadBundledTosDocument()
            privacyDoc = legalRepository.loadBundledPrivacyDocument()
        } finally {
            isLoadingDocs = false
        }
    }

    Dialog(
        onDismissRequest = {
            if (isReadOnly) onDismissReadOnly()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = isReadOnly,
            dismissOnClickOutside = isReadOnly
        )
    ) {
        val cs = MaterialTheme.colorScheme
        val tokens = VlTheme.tokens

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            VlSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(24.dp)),
                customRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cs.surface)
                ) {
                    // ── ШАПКА ──────────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainerLow)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(cs.primary.copy(alpha = 0.14f))
                                        .border(1.dp, cs.primary.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (isReadOnly) "Правовая информация" else "Юридические условия",
                                        style = TextStyle(
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurface
                                        )
                                    )
                                    Text(
                                        text = "VisorLink v$currentVersion",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = cs.primary
                                        )
                                    )
                                }
                            }

                            if (isReadOnly) {
                                IconButton(onClick = onDismissReadOnly) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Закрыть",
                                        tint = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Переключатель вкладок
                        VlSegmentedControl(
                            labels = listOf("📜 Условия (ToS)", "🔒 Приватность"),
                            selectedIndex = selectedTab,
                            onSelected = { selectedTab = it }
                        )
                    }

                    HorizontalDivider(color = cs.outlineVariant, thickness = 1.dp)

                    // ── КОНТЕНТ ДОКУМЕНТА (Scrollable) ─────────────────────────────────
                    val activeDoc = if (selectedTab == 0) tosDoc else privacyDoc
                    val scrollState = rememberScrollState()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (isLoadingDocs && activeDoc == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = cs.primary)
                            }
                        } else if (activeDoc != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Заголовок документа
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = activeDoc.title,
                                        style = TextStyle(
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurface
                                        )
                                    )
                                    if (!activeDoc.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = activeDoc.subtitle,
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                color = cs.onSurfaceVariant,
                                                lineHeight = 18.sp
                                            )
                                        )
                                    }
                                    Text(
                                        text = "Редакция: v${activeDoc.version ?: currentVersion}${if (activeDoc.lastUpdated != null) " • ${activeDoc.lastUpdated}" else ""}",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = cs.primary
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                // Вводный блок (introHtml)
                                if (!activeDoc.introHtml.isNullOrBlank()) {
                                    LegalHtmlContent(html = activeDoc.introHtml)
                                }

                                // Разделы документа
                                for (section in activeDoc.sections) {
                                    LegalSectionCard(section = section)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = cs.outlineVariant, thickness = 1.dp)

                    // ── ФУТЕР (Чекбокс + Кнопки) ───────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainerLow)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isReadOnly) {
                            // Чекбокс согласия
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isAgreed = !isAgreed }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isAgreed,
                                    onCheckedChange = { isAgreed = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = cs.primary,
                                        checkmarkColor = cs.onPrimary
                                    )
                                )
                                Text(
                                    text = "Я принимаю Условия использования и Политику конфиденциальности VisorLink (v$currentVersion)",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = cs.onSurface
                                    )
                                )
                            }

                            // Кнопки действий
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onLogout,
                                    modifier = Modifier.weight(0.35f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = cs.error
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(cs.error.copy(alpha = 0.5f))
                                    )
                                ) {
                                    Text(
                                        text = "Выйти",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isAgreed && !isSubmitting) {
                                            isSubmitting = true
                                            onAccept()
                                        }
                                    },
                                    modifier = Modifier.weight(0.65f),
                                    enabled = isAgreed && !isSubmitting,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cs.primary,
                                        contentColor = cs.onPrimary,
                                        disabledContainerColor = cs.surfaceContainerHighest,
                                        disabledContentColor = cs.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = cs.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = "Принять и продолжить",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            // Кнопка закрытия для режима просмотра
                            Button(
                                onClick = onDismissReadOnly,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cs.surfaceContainerHigh,
                                    contentColor = cs.onSurface
                                )
                            ) {
                                Text("Закрыть", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка отдельной секции юридического документа.
 */
@Composable
fun LegalSectionCard(
    section: LegalSection,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainer.copy(alpha = 0.60f))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Заголовок раздела с номером и бейджем
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = section.number,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.primary
                            )
                        )
                    }

                    Text(
                        text = section.title,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                    )
                }

                if (!section.badge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHigh)
                            .border(1.dp, cs.outlineVariant, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = section.badge,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Ключевые тезисы (keyPoints)
            if (section.keyPoints.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceContainerLow)
                        .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (point in section.keyPoints) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "✓",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )
                            )
                            Text(
                                text = point,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = cs.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // Содержимое раздела (с HTML-тегами)
            LegalHtmlContent(html = section.content)
        }
    }
}
