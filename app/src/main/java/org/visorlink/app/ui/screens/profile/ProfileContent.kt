package org.visorlink.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCardPosition
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.ProfileLayout
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.components.AdminBadge
import org.visorlink.app.ui.components.AvatarContent
import org.visorlink.app.ui.components.LinkifiedText
import org.visorlink.app.ui.components.ProBadge
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlPresenceDot
import org.visorlink.app.ui.components.liquidPillCardSlideOut
import org.visorlink.app.ui.idcard.ProfileIdCard
import org.visorlink.app.ui.idcard.ProfileModeMark
import org.visorlink.app.ui.theme.VlTheme

/*
 * Общие части своего ([ProfileScreen]) и чужого ([OtherProfileScreen]) профиля. Раньше обе
 * страницы держали по копии этого кода, и правки расходились.
 */

/**
 * Фон профиля: фото владельца под вуалью цвета фона темы, без фото — фоновое свечение.
 * Вуаль раньше была белой 20 % в светлой теме: тёмный текст светлой темы на тёмной или
 * пёстрой фотографии пропадал. Теперь под текстом всегда 60–85 % фона темы.
 */
@Composable
internal fun BoxScope.ProfileBackdrop(bgUrl: String?) {
    if (bgUrl == null) {
        VlAmbientGlow()
        return
    }
    AsyncImage(
        model = bgUrl,
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop
    )
    ProfileBackdropScrim(Modifier.matchParentSize())
}

/** Вуаль поверх фото: сверху слабее (фото видно за аватаром), к тексту плотнее. */
@Composable
internal fun ProfileBackdropScrim(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val isDark = bg.luminance() < 0.5f
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to bg.copy(alpha = if (isDark) 0.40f else 0.50f),
                0.3f to bg.copy(alpha = if (isDark) 0.62f else 0.74f),
                1f to bg.copy(alpha = if (isDark) 0.80f else 0.86f),
            )
        )
    )
}

/**
 * Шапка профиля: аватар, имя, ник. Компактная раскладка — в строку, остальные — баннер над
 * аватаром. [onAvatarClick] — смена аватара в режиме редактирования своего профиля.
 */
@Composable
internal fun ProfileHeader(
    user: UserProfile,
    appearance: ProfileAppearance,
    isMe: Boolean,
    onAvatarClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bannerUrl = appearance.bannerUrl
    if (appearance.layout == ProfileLayout.COMPACT) {
        if (bannerUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(cs.surfaceContainerHighest)
            ) {
                AsyncImage(model = bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (bannerUrl != null) 16.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(user, 80.dp, border = 2.dp, onClick = onAvatarClick)
            Spacer(Modifier.width(20.dp))
            Column {
                ProfileName(user, appearance, isMe, emojiSize = 20)
                Text("@${user.username}", style = MaterialTheme.typography.bodyLarge, color = cs.primary)
            }
        }
    } else {
        if (bannerUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(bottom = 50.dp)
                        .background(cs.surfaceContainerHighest)
                ) {
                    AsyncImage(model = bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                ProfileAvatar(user, 110.dp, border = 4.dp, onClick = onAvatarClick)
            }
        } else {
            Box(Modifier.padding(top = 24.dp)) {
                ProfileAvatar(user, 130.dp, border = 4.dp, onClick = onAvatarClick)
            }
        }

        Spacer(Modifier.height(14.dp))
        ProfileName(user, appearance, isMe, emojiSize = 22)
        Spacer(Modifier.height(2.dp))
        Text(
            "@${user.username}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = cs.primary
        )
    }
}

@Composable
private fun ProfileAvatar(user: UserProfile, size: Dp, border: Dp, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val shape = VlTheme.tokens.shapes.avatar
    Box(
        modifier = Modifier
            .size(size)
            .background(cs.surfaceContainerLow, shape)
            // Кант цвета поверхности отделяет аватар от баннера и фона
            .border(border, cs.surface, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        AvatarContent(user, size)
    }
}

@Composable
private fun ProfileName(user: UserProfile, appearance: ProfileAppearance, isMe: Boolean, emojiSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            user.displayName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        ProfileModeMark(user.idMode, isMe = isMe)
        val emojis = appearance.emojis
        if (!emojis.isNullOrEmpty()) {
            Text(emojis, modifier = Modifier.padding(start = 6.dp), fontSize = emojiSize.sp)
        }
    }
}

/**
 * Тело профиля: UID (отладка), «В сети», значки админа и PRO, описание, ID-карта в нижнем
 * слоте. [viewer] — смотрящий, для правил показа карты; у своего профиля — `null`.
 * [idCardSlot] = false — карта уже стоит шапкой (вкладка «Профиль»), второй раз её не рисуем.
 */
@Composable
internal fun ProfileDetails(user: UserProfile, isMe: Boolean, viewer: UserProfile? = null, idCardSlot: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(24.dp)
            .liquidPillCardSlideOut(index = 1),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DebugUidBadge(uid = user.uid)
        if (user.online) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VlPresenceDot(online = true)
                Text(
                    stringResource(R.string.chat_status_online),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (user.isAdmin) {
            Spacer(Modifier.height(16.dp))
            AdminBadge()
        }

        if (user.isProActive()) {
            Spacer(Modifier.height(12.dp))
            ProBadge()
        }

        if (user.bio.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            VlCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = VlTheme.tokens.shapes.card
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinkifiedText(
                        text = user.bio,
                        color = cs.onSurface,
                        linkColor = cs.primary,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (idCardSlot) ProfileIdCard(user, isMe = isMe, slot = IdCardPosition.BOTTOM, viewer = viewer)
    }
}
