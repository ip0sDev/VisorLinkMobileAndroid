package org.visorlink.app.ui.components.chatlist

import org.visorlink.app.ui.theme.VlTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

/**
 * Аватар-кнопка пользователя в шапке списка чатов.
 * Помещена в рамку и рельеф [VlTopAppBarButton] 36×36 dp с утопленной фото-линзой аватара.
 */
@Composable
fun AvatarChip(
    avatarUrl: String?,
    displayName: String,
    hapticEnabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val avatarShape = tokens.shapes.avatar
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "?"

    VlTopAppBarButton(
        onClick = {
            haptic.perform(HapticType.CLICK, hapticEnabled)
            onClick()
        },
        modifier = Modifier.size(36.dp),
        contentPadding = PaddingValues(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(avatarShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                CachedImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = cs.primaryContainer,
                    shape = avatarShape,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
