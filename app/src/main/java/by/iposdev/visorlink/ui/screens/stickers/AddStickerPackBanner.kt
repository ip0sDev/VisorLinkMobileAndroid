package by.iposdev.visorlink.ui.screens.stickers

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

// ════════════════════════════════════════════════════════════════════════════════
//  AddStickerPackBanner
//  Встраивается под bubble стикера, если packId != null и пак ещё не добавлен
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun AddStickerPackBanner(
    packId: String,
    packName: String,
    packEmoji: String,
    viewModel: StickerPackViewModel = koinViewModel()
) {
    var bannerState by remember(packId) { mutableStateOf<AddPackBannerState>(AddPackBannerState.Idle) }
    var alreadyHas by remember(packId) { mutableStateOf<Boolean?>(null) }

    // Проверяем, есть ли пак у пользователя (один раз)
    LaunchedEffect(packId) {
        alreadyHas = viewModel.hasPack(packId)
    }

    // Автосброс ошибки
    LaunchedEffect(bannerState) {
        if (bannerState is AddPackBannerState.Error) {
            delay(2000)
            bannerState = AddPackBannerState.Idle
        }
    }

    // Не показываем если уже добавлен или ещё проверяем
    if (alreadyHas == true || alreadyHas == null) return

    AnimatedVisibility(
        visible = bannerState !is AddPackBannerState.Done || true,
        enter = fadeIn(tween(250)) + expandVertically(),
        exit = fadeOut(tween(200)) + shrinkVertically()
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(packEmoji, fontSize = 20.sp)
                Column(Modifier.weight(1f)) {
                    when (val s = bannerState) {
                        is AddPackBannerState.Done -> {
                            Text(
                                "✓ Пак добавлен",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is AddPackBannerState.Error -> {
                            Text(
                                "Ошибка — попробуйте ещё раз",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                "Добавить пак «$packName»",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = bannerState,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "banner_btn"
                ) { state ->
                    when (state) {
                        is AddPackBannerState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        is AddPackBannerState.Done -> {
                            Icon(
                                Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        else -> {
                            IconButton(
                                onClick = {
                                    viewModel.addForeignPack(packId) { result ->
                                        bannerState = result
                                        if (result is AddPackBannerState.Done) {
                                            alreadyHas = true
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add, "Добавить пак",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}