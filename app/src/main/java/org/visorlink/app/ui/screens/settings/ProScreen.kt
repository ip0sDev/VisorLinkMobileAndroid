package org.visorlink.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.CosmeticsRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.theme.VlCategoryTint
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import java.text.SimpleDateFormat
import java.util.*

private val proDateFormat by lazy {
    SimpleDateFormat("dd.MM.yyyy", Locale.US)
}

private val ProGold = Color(0xFFC5A059)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    onOpenSignal: () -> Unit,
    onOpenChatFolders: () -> Unit = {},
    viewModel: ProViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject()
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val proState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by userRepository.currentUserFlow().collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(proState.successMessage) {
        proState.successMessage?.let {
            haptic.perform(HapticType.SUCCESS)
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(proState.error) {
        proState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.pro_title)) },
                navigationIcon = {
                    org.visorlink.app.ui.components.VlTopAppBarButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.custom_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val p = profile
        if (p == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 48.dp)
            ) {
                ProStatusBanner(
                    profile = p,
                    proViewModel = viewModel,
                    proState = proState,
                    onOpenSignal = onOpenSignal
                )

                ProPerksSection(
                    profile = p,
                    proViewModel = viewModel,
                    onOpenChatFolders = onOpenChatFolders
                )

                CosmeticsSection(
                    profile = p,
                    proViewModel = viewModel,
                    proState = proState
                )
            }
        }
    }
}

/**
 * Компактная карточка-переход на экран PRO для главного экрана настроек.
 */
@Composable
fun CompactProTile(
    profile: UserProfile,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isActive = profile.isProActive()
    val eternal = profile.isEternalPro

    val statusText = when {
        eternal -> "Бесконечный PRO"
        isActive -> "Активен до ${profile.proUntil?.toDate()?.let { proDateFormat.format(it) } ?: ""}"
        else -> "Премиум-возможности и бонусы"
    }

    val subtitleParts = mutableListOf(statusText)
    if (isActive) {
        subtitleParts.add("${profile.bits} Бит")
        if (profile.streak > 0) {
            subtitleParts.add("🔥 ${profile.streak} дн.")
        }
    }

    VlSettingsSection(
        title = "VisorLink PRO",
        isPremium = true
    ) {
        VlSettingsItem(
            icon = if (isActive) Icons.Rounded.WorkspacePremium else Icons.Rounded.Stars,
            iconColor = if (isActive) ProGold else cs.onSurfaceVariant,
            title = "VisorLink PRO",
            subtitle = subtitleParts.joinToString(" • "),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ProGold.copy(alpha = 0.15f))
                                .border(1.dp, ProGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "PRO",
                                color = ProGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant
                    )
                }
            },
            onClick = onClick
        )
    }
}

@Composable
fun ProStatusBanner(
    profile: UserProfile,
    proViewModel: ProViewModel,
    proState: ProUiState,
    onOpenSignal: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isActive = profile.isProActive()
    val eternal = profile.isEternalPro
    val monthBits = ProViewModel.planBits["month"] ?: 1000
    val yearBits = ProViewModel.planBits["year"] ?: 10000

    var confirmYear by remember { mutableStateOf(false) }

    VlSettingsSection(
        title = "VisorLink PRO",
        isPremium = true
    ) {
        VlSettingsItem(
            icon = if (isActive) Icons.Rounded.WorkspacePremium else Icons.Rounded.Stars,
            iconColor = if (isActive) ProGold else cs.onSurfaceVariant,
            title = if (isActive) "Статус: Активен" else "Статус: Неактивен",
            subtitle = if (eternal) "♾️ Бесконечный PRO" else if (isActive) "До ${profile.proUntil?.toDate()?.let { proDateFormat.format(it) } ?: ""}" else "Разблокируйте золотой бейдж"
        )

        VlSettingsItem(
            icon = Icons.Rounded.Toll,
            iconColor = ProGold,
            title = "Ваши Биты",
            trailing = {
                Text(
                    profile.bits.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ProGold
                )
            }
        )

        VlSettingsItem(
            icon = Icons.Rounded.LocalFireDepartment,
            iconColor = Color(0xFFE11D48),
            title = "Стрик: ${profile.streak} дн.",
            subtitle = "Открыть «Сигнал»",
            trailing = {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
            },
            onClick = onOpenSignal
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Золотой бейдж PRO, альбомы до 20 фото, невидимка, бонусы ID-карт и полная кастомизация профиля: свои фоны и GIF, цвет профиля, шрифты, раскладки и эмодзи-статусы!",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Text(
                "Отправляйте сообщения каждый день, чтобы растить стрик: до 50 Бит в день и бонусы за 7, 30 и каждые 100 дней. Пропустите 48 часов — стрик сгорит.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                lineHeight = 18.sp
            )

            if (!eternal) {
                // Покупка на месяц (30 дней)
                Button(
                    onClick = { proViewModel.buyPro(useTrial = false, plan = "month") },
                    enabled = !proState.isLoading && profile.bits >= monthBits,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProGold, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isActive) "Продлить PRO (+30 дней)" else "Купить PRO (30 дней)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text("$monthBits Бит", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                // Покупка на год (365 дней)
                if (confirmYear) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Спишется $yearBits Bits, к PRO добавится 365 дней.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { confirmYear = false },
                                enabled = !proState.isLoading,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Отмена")
                            }
                            Button(
                                onClick = {
                                    confirmYear = false
                                    proViewModel.buyPro(useTrial = false, plan = "year")
                                },
                                enabled = !proState.isLoading,
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ProGold, contentColor = Color.White),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Купить год")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmYear = true },
                        enabled = !proState.isLoading && profile.bits >= yearBits,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ProGold)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isActive) "Продлить PRO на год (+365 дней)" else "PRO на год (365 дней)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ProGold
                            )
                            Text("$yearBits Бит", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ProGold)
                        }
                    }
                }

                // Триал
                if (!profile.trialUsed && !isActive) {
                    OutlinedButton(
                        onClick = { proViewModel.buyPro(useTrial = true) },
                        enabled = !proState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Активировать Триал (1 день)")
                    }
                }
            }
        }
    }
}

@Composable
fun ProPerksSection(
    profile: UserProfile,
    proViewModel: ProViewModel,
    onOpenChatFolders: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val pro = profile.isProActive()

    val perks = listOf(
        Pair(Icons.Rounded.FolderCopy, "Папки чатов — до 10 вкладок над списком"),
        Pair(Icons.Rounded.PhotoLibrary, "Альбомы до 20 фото вместо 10"),
        Pair(Icons.Rounded.VisibilityOff, "Невидимка: скрыть «в сети» и отметки о прочтении"),
        Pair(Icons.Rounded.Badge, "ID-карты: две прокрутки в неделю и +5 слотов"),
        Pair(Icons.Rounded.WorkspacePremium, "Две PRO-карты ID — Aurum и Noir"),
        Pair(Icons.Rounded.ScheduleSend, "Отложенные сообщения — до 20 запланированных"),
        Pair(Icons.Rounded.Insights, "«Сигнал»: собеседники за всё время"),
        Pair(Icons.Rounded.EmojiEmotions, "Эмодзи-статус и цвет ника бесплатно"),
        Pair(Icons.Rounded.Palette, "Кастомизация профиля: фон, GIF, цвет, шрифт, раскладка")
    )

    VlSettingsSection(
        title = "Возможности PRO",
        isPremium = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((icon, text) in perks) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = ProGold, modifier = Modifier.size(16.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                }
            }
        }

        VlSettingsItem(
            icon = Icons.Rounded.FolderCopy,
            iconColor = if (pro) ProGold else cs.onSurfaceVariant,
            title = "Папки чатов",
            subtitle = "Умные вкладки и фильтры над списком чатов",
            trailing = {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
            },
            onClick = onOpenChatFolders
        )

        // Тумблер hideOnline
        VlSettingsItem(
            icon = if (pro) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock,
            iconColor = if (pro) null else cs.onSurfaceVariant,
            title = "Скрыть «в сети»",
            subtitle = "Собеседники увидят «был(а) недавно» вместо статуса и времени захода.",
            trailing = {
                VlSwitch(
                    checked = pro && profile.hidesOnline,
                    onCheckedChange = { v ->
                        if (pro) {
                            proViewModel.updateUserSettings(profile.uid, "hideOnline", v)
                        }
                    }
                )
            }
        )

        // Тумблер hideReads
        VlSettingsItem(
            icon = if (pro) Icons.Rounded.DoneAll else Icons.Rounded.Lock,
            iconColor = if (pro) null else cs.onSurfaceVariant,
            title = "Скрыть «прочитано»",
            subtitle = "Вы читаете сообщения, но собеседники не видят отметок о прочтении.",
            trailing = {
                VlSwitch(
                    checked = pro && profile.hidesReads,
                    onCheckedChange = { v ->
                        if (pro) {
                            proViewModel.updateUserSettings(profile.uid, "hideReads", v)
                        }
                    }
                )
            }
        )

        if (!pro) {
            Text(
                "Настройки невидимки доступны с PRO.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
fun CosmeticsSection(
    profile: UserProfile,
    proViewModel: ProViewModel,
    proState: ProUiState
) {
    val cs = MaterialTheme.colorScheme
    val light = cs.surface.luminance() > 0.5f
    val pro = profile.isProActive()
    val canEmoji = pro || profile.isEmojiStatusUnlocked
    val canColor = pro || profile.isNameColorUnlocked
    val emoji = profile.emojiStatus
    val color = profile.nameColor
    val name = profile.displayName.ifEmpty { profile.username }
    val nameInk = if (canColor) CosmeticsRepository.readableNameColor(color, light = light) else null

    var customEmojiDraft by remember { mutableStateOf("") }
    var unlockDialogItem by remember { mutableStateOf<String?>(null) }

    if (unlockDialogItem != null) {
        val itemKey = unlockDialogItem!!
        val title = CosmeticsRepository.itemTitles[itemKey] ?: itemKey
        val price = CosmeticsRepository.unlockPrices[itemKey] ?: 300
        VlAlertDialog(
            onDismissRequest = { unlockDialogItem = null },
            title = { Text("Купить «$title»?") },
            text = { Text("Спишется $price Bits. Покупка навсегда.") },
            actions = {
                VlDialogButton(onClick = { unlockDialogItem = null }) { Text("Отмена") }
                VlDialogButton(
                    isPrimary = true,
                    onClick = {
                        unlockDialogItem = null
                        proViewModel.unlockCosmetic(itemKey)
                    }
                ) { Text("Купить за $price") }
            }
        )
    }

    VlSettingsSection(
        title = "Статус и ник",
        isPremium = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Эмодзи рядом с именем видно везде, цвет ника — в группах. С PRO — бесплатно, без PRO — разовая покупка навсегда.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

            // Превью «как в группе»
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainerHighest)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = nameInk ?: cs.primary
                        )
                        if (canEmoji && !emoji.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(emoji, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("Так вас увидят в группе", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                }
            }

            // Заголовок Эмодзи-статус
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Эмодзи-статус",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                if (canEmoji && !emoji.isNullOrBlank()) {
                    TextButton(
                        onClick = { proViewModel.setCosmetics(emojiStatus = null) },
                        enabled = !proState.isCosmeticsLoading
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Убрать")
                    }
                }
            }

            if (canEmoji) {
                // Быстрые эмодзи (2 ряда по 8)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val chunks = remember { CosmeticsRepository.quickEmojis.chunked(8) }
                    for (row in chunks) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (e in row) {
                                val selected = emoji == e
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) cs.primary.copy(alpha = 0.18f) else cs.surfaceContainerHighest.copy(alpha = 0.5f))
                                        .then(if (selected) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(10.dp)) else Modifier)
                                        .clickable(enabled = !proState.isCosmeticsLoading) {
                                            proViewModel.setCosmetics(emojiStatus = e)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(e, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                // Свой эмодзи
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customEmojiDraft,
                        onValueChange = { customEmojiDraft = it.take(8) },
                        placeholder = { Text("Свой эмодзи") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val v = customEmojiDraft.trim()
                            if (v.isNotEmpty()) {
                                proViewModel.setCosmetics(emojiStatus = v)
                                customEmojiDraft = ""
                            }
                        },
                        enabled = !proState.isCosmeticsLoading && customEmojiDraft.trim().isNotEmpty()
                    ) {
                        Text("Поставить")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(
                        "С PRO или за 300 Bits навсегда",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { unlockDialogItem = "emojiStatus" },
                        enabled = !proState.isCosmeticsLoading && profile.bits >= 300
                    ) {
                        Icon(Icons.Rounded.Toll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Купить за 300")
                    }
                }
            }

            // Заголовок Цвет ника
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Цвет ника",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                if (canColor && !color.isNullOrBlank()) {
                    TextButton(
                        onClick = { proViewModel.setCosmetics(nameColor = null) },
                        enabled = !proState.isCosmeticsLoading
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Убрать")
                    }
                }
            }

            if (canColor) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val chunks = remember { CosmeticsRepository.nameColors.chunked(7) }
                    for (row in chunks) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (hex in row) {
                                val isSelected = color?.uppercase() == hex
                                val chipColor = CosmeticsRepository.readableNameColor(hex, light = light) ?: Color.Gray
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                        .then(if (isSelected) Modifier.border(2.5.dp, cs.onSurface, CircleShape) else Modifier)
                                        .clickable(enabled = !proState.isCosmeticsLoading) {
                                            proViewModel.setCosmetics(nameColor = hex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = cs.surface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(
                        "С PRO или за 500 Bits навсегда",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { unlockDialogItem = "nameColor" },
                        enabled = !proState.isCosmeticsLoading && profile.bits >= 500
                    ) {
                        Icon(Icons.Rounded.Toll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Купить за 500")
                    }
                }
            }

            if (proState.cosmeticsError != null) {
                Text(
                    proState.cosmeticsError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
