package org.visorlink.app.ui.screens.legal

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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import org.visorlink.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.visorlink.app.data.model.LegalDocument
import org.visorlink.app.data.model.LegalSection
import org.visorlink.app.data.repository.LegalRepository
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.theme.VlDepth
import org.visorlink.app.ui.theme.VlTheme
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
    var agreeTos by remember { mutableStateOf(false) }
    var agreePrivacy by remember { mutableStateOf(false) }
    var agreePersonalData by remember { mutableStateOf(false) }
    var agreeCrossBorder by remember { mutableStateOf(false) }
    var agreeAge14 by remember { mutableStateOf(false) }

    val allAgreed = agreeTos && agreePrivacy && agreePersonalData && agreeCrossBorder && agreeAge14
    var isSubmitting by remember { mutableStateOf(false) }

    var tosDoc by remember { mutableStateOf<LegalDocument?>(null) }
    var privacyDoc by remember { mutableStateOf<LegalDocument?>(null) }
    var isLoadingDocs by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    // Загрузка документов строго с сервера (без фоллбэка на локальные ассеты)
    LaunchedEffect(reloadTrigger) {
        isLoadingDocs = true
        loadError = null
        try {
            val loadedTos = legalRepository.getTosDocument()
            val loadedPrivacy = legalRepository.getPrivacyPolicyDocument()
            tosDoc = loadedTos
            privacyDoc = loadedPrivacy
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load documents"
            tosDoc = null
            privacyDoc = null
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
                        if (isLoadingDocs) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = cs.primary)
                            }
                        } else if (loadError != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = cs.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.legal_load_error_title),
                                    style = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSurface
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.legal_load_error_desc),
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = cs.onSurfaceVariant,
                                        lineHeight = 18.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = { reloadTrigger++ },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cs.primary,
                                        contentColor = cs.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.legal_retry_button), fontWeight = FontWeight.SemiBold)
                                }
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

                    // ── ФУТЕР (5 Чекбоксов согласия + Кнопки) ───────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainerLow)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isReadOnly) {
                            if (loadError == null && !isLoadingDocs) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // 1. ToS
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { agreeTos = !agreeTos }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = agreeTos,
                                            onCheckedChange = { agreeTos = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = cs.primary,
                                                checkmarkColor = cs.onPrimary
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.legal_consent_checkbox_tos),
                                            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = cs.onSurface)
                                        )
                                    }

                                    // 2. Privacy Policy
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { agreePrivacy = !agreePrivacy }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = agreePrivacy,
                                            onCheckedChange = { agreePrivacy = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = cs.primary,
                                                checkmarkColor = cs.onPrimary
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.legal_consent_checkbox_privacy),
                                            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = cs.onSurface)
                                        )
                                    }

                                    // 3. Согласие на обработку персональных данных (ст. 5 Закона № 99-З)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { agreePersonalData = !agreePersonalData }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = agreePersonalData,
                                            onCheckedChange = { agreePersonalData = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = cs.primary,
                                                checkmarkColor = cs.onPrimary
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.legal_consent_checkbox_personal_data),
                                            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = cs.onSurface)
                                        )
                                    }

                                    // 4. Трансграничная передача данных (ст. 9 Закона № 99-З)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { agreeCrossBorder = !agreeCrossBorder }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = agreeCrossBorder,
                                            onCheckedChange = { agreeCrossBorder = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = cs.primary,
                                                checkmarkColor = cs.onPrimary
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.legal_consent_checkbox_cross_border),
                                            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = cs.onSurface)
                                        )
                                    }

                                    // 5. Возрастной ценз 14+
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { agreeAge14 = !agreeAge14 }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = agreeAge14,
                                            onCheckedChange = { agreeAge14 = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = cs.primary,
                                                checkmarkColor = cs.onPrimary
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.legal_consent_checkbox_age14),
                                            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, color = cs.onSurface)
                                        )
                                    }
                                }
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
                                        if (allAgreed && !isSubmitting && loadError == null) {
                                            isSubmitting = true
                                            onAccept()
                                        }
                                    },
                                    modifier = Modifier.weight(0.65f),
                                    enabled = allAgreed && !isSubmitting && loadError == null,
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
