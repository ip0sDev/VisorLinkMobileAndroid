package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.repository.UserGiftRepository
import org.visorlink.app.data.repository.UserGifts
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.data.repository.giftErrorText
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.idcard.PricePill
import org.visorlink.app.ui.idcard.SkinActionButton
import org.visorlink.app.ui.idcard.SkinError
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

private enum class GiftTab { PRO, POSTCARD, SKIN }

/**
 * Диалог выбора подарка в чате: PRO (7 / 30 дней), открытка или ID-карта из инвентаря.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftPickerBottomSheet(
    chatId: String,
    recipientName: String,
    isEternalPro: Boolean,
    onDismiss: () -> Unit,
    onOpenSkinPicker: () -> Unit,
    userGiftRepository: UserGiftRepository = koinInject(),
    userRepository: UserRepository = koinInject()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()

    val myProfile by remember(userRepository) { userRepository.currentUserFlow() }.collectAsStateWithLifecycle(initialValue = null)
    val userBits = myProfile?.bits ?: 0

    var selectedTab by remember { mutableStateOf(GiftTab.PRO) }
    var selectedProKind by remember { mutableStateOf("pro_month") }
    var selectedDesign by remember { mutableStateOf("aurora") }
    var note by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val currentPrice = when (selectedTab) {
        GiftTab.PRO -> UserGifts.proPrices[selectedProKind] ?: 1000
        GiftTab.POSTCARD -> UserGifts.postcardPrices[selectedDesign] ?: 35
        GiftTab.SKIN -> 0
    }

    val canSend = selectedTab != GiftTab.SKIN &&
            !(selectedTab == GiftTab.PRO && isEternalPro) &&
            userBits >= currentPrice &&
            !isBusy && !isSent

    fun submit() {
        if (!canSend) return
        isBusy = true
        errorText = null
        scope.launch {
            try {
                userGiftRepository.send(
                    chatId = chatId,
                    kind = if (selectedTab == GiftTab.PRO) selectedProKind else "postcard",
                    design = if (selectedTab == GiftTab.POSTCARD) selectedDesign else null,
                    note = note.trim()
                )
                haptic.perform(HapticType.SUCCESS, true)
                isSent = true
                delay(800)
                sheetState.hide()
                onDismiss()
            } catch (e: Exception) {
                errorText = giftErrorText(e)
                isBusy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.gift_picker_kicker),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.gift_picker_title, recipientName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            PricePill(userBits, fg = VlTheme.tokens.status.warning, bg = VlTheme.tokens.status.warning.copy(alpha = 0.14f))

            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(VlTheme.tokens.shapes.card)
            ) {
                GiftTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            if (!isBusy) {
                                selectedTab = tab
                                errorText = null
                            }
                        },
                        text = {
                            Text(
                                when (tab) {
                                    GiftTab.PRO -> "PRO"
                                    GiftTab.POSTCARD -> stringResource(R.string.gift_postcard)
                                    GiftTab.SKIN -> stringResource(R.string.gift_skin)
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }

            // Контент вкладки
            when (selectedTab) {
                GiftTab.PRO -> {
                    if (isEternalPro) {
                        Text(
                            text = stringResource(R.string.gift_pro_eternal_hint, recipientName),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            UserGifts.proPrices.forEach { (kind, price) ->
                                val active = selectedProKind == kind
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(VlTheme.tokens.shapes.card)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .border(
                                            width = if (active) 2.dp else 1.dp,
                                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = VlTheme.tokens.shapes.card
                                        )
                                        .clickable(enabled = !isBusy) { selectedProKind = kind }
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("💎", fontSize = 24.sp)
                                        Text(
                                            text = UserGifts.kindTitles[kind] ?: kind,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "$price Bits",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                GiftTab.POSTCARD -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserGifts.postcardPrices.forEach { (design, price) ->
                                val active = selectedDesign == design
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(VlTheme.tokens.shapes.card)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .border(
                                            width = if (active) 2.dp else 1.dp,
                                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = VlTheme.tokens.shapes.card
                                        )
                                        .clickable(enabled = !isBusy) { selectedDesign = design }
                                        .padding(8.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Postcard(
                                            design = design,
                                            small = true,
                                            play = active,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = UserGifts.designTitles[design] ?: design,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "$price Bits",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.gift_postcard_hint),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                GiftTab.SKIN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.gift_skin_hint),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        SkinActionButton(
                            text = stringResource(R.string.gift_choose_skin),
                            onClick = {
                                onDismiss()
                                onOpenSkinPicker()
                            },
                            icon = Icons.Default.Person
                        )
                    }
                }
            }

            if (selectedTab != GiftTab.SKIN) {
                VlTextField(
                    value = note,
                    onValueChange = { if (it.length <= UserGifts.NOTE_MAX) note = it.replace('\n', ' ') },
                    label = stringResource(R.string.gift_note_label),
                    placeholder = stringResource(R.string.gift_note_placeholder),
                    supportingText = "${note.length}/${UserGifts.NOTE_MAX}",
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedTab == GiftTab.PRO && !isEternalPro) {
                    Text(
                        text = stringResource(R.string.gift_revoke_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (userBits < currentPrice) {
                    SkinError(stringResource(R.string.gift_not_enough_bits))
                }
            }

            if (errorText != null) {
                SkinError(errorText!!)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                SkinActionButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    primary = false,
                    enabled = !isBusy
                )
                if (selectedTab != GiftTab.SKIN) {
                    val buttonText = when {
                        isSent -> stringResource(R.string.gift_sent_status)
                        isBusy -> stringResource(R.string.gift_sending_status)
                        else -> stringResource(R.string.gift_send_action, currentPrice)
                    }
                    SkinActionButton(
                        text = buttonText,
                        onClick = ::submit,
                        enabled = canSend,
                        icon = Icons.AutoMirrored.Filled.Send
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
