package org.visorlink.app.ui.idcard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardPosition
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.IdModeGlyph
import org.visorlink.app.ui.components.idcard.VlIdCard

/**
 * ID-карта в профиле (спека §4a, веб: ProfileIdCard.jsx), в одном из мест — выбор владельца
 * (`customization.idCardPosition`, не PRO). Рисуется, только если [slot] совпадает с выбором.
 *
 * Свою видно всегда; чужую — только если у смотрящего особый режим (правила Firestore иначе
 * её и не отдадут: `PERMISSION_DENIED` — норма, блок просто не рисуется). В маске профили
 * выглядят обычными — и свой, и чужие.
 */
@Composable
fun ProfileIdCard(profile: UserProfile, isMe: Boolean, slot: IdCardPosition, viewer: UserProfile? = null) {
    if (IdCardPosition.of(profile.customization) != slot) return
    val state = LocalIdModeState.current
    val canSee = state.cardsVisible && (isMe || state.myMode.isSpecial)
    if (!canSee) return
    val repo: IdCardRepository = koinInject()
    val cardState by remember(profile.uid) { repo.cardFlow(profile.uid) }.collectAsState(initial = IdCardState.Loading)
    val card = (cardState as? IdCardState.Ready)?.card ?: return

    // Лёгкие признаки из кастомизации (акцент — линия, эмодзи — наклейка) — по правилам PRO
    val appearance = ProfileAppearance.resolve(owner = profile, viewer = viewer ?: profile)
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        VlIdCard(
            card = card,
            person = IdCardPerson(profile.displayName, profile.username, profile.avatarUrl),
            width = 296.dp,
            accent = appearance.accentHexColor ?: appearance.accent?.seedColor,
            emojis = appearance.emojis.orEmpty(),
        )
    }
}

/**
 * Шапка своего профиля на вкладке «Профиль» (флаг `enable_profile_navbar`): сразу ID-карта
 * вместо аватара — аватар и имя и так на ней. Карты нет или она скрыта маской — [fallback]
 * (обычная шапка с аватаром). Пока карта грузится, под неё держится место, чтобы шапка не
 * прыгала из аватара в карту.
 */
@Composable
fun ProfileIdCardHeader(profile: UserProfile, fallback: @Composable () -> Unit) {
    if (!LocalIdModeState.current.cardsVisible) {
        fallback()
        return
    }
    if (profile.uid.isBlank()) {
        IdCardHeaderSpace()
        return
    }
    val repo: IdCardRepository = koinInject()
    val cardState by remember(profile.uid) { repo.cardFlow(profile.uid) }.collectAsState(initial = IdCardState.Loading)
    when (val s = cardState) {
        IdCardState.Loading -> IdCardHeaderSpace()
        is IdCardState.Ready -> s.card?.let { IdCardHeader(it, profile) } ?: fallback()
        else -> fallback()
    }
}

/** Карта во всю ширину экрана (не больше [IdCardHeaderMaxWidth]) с подсказкой «Оборот» под ней. */
@Composable
fun IdCardHeader(card: IdCard, profile: UserProfile) {
    // Свой профиль — в своём оформлении: акцент — линия на карте, эмодзи — наклейка
    val appearance = ProfileAppearance.resolve(owner = profile, viewer = profile)
    BoxWithConstraints(IdCardHeaderModifier, contentAlignment = Alignment.TopCenter) {
        VlIdCard(
            card = card,
            person = IdCardPerson(profile.displayName, profile.username, profile.avatarUrl),
            width = minOf(maxWidth, IdCardHeaderMaxWidth),
            accent = appearance.accentHexColor ?: appearance.accent?.seedColor,
            emojis = appearance.emojis.orEmpty(),
        )
    }
}

@Composable
private fun IdCardHeaderSpace() {
    BoxWithConstraints(IdCardHeaderModifier) {
        // Высота карты 85.6 × 54 плюс строка «Оборот» под ней
        Spacer(Modifier.height(minOf(maxWidth, IdCardHeaderMaxWidth) * (54f / 85.6f) + 32.dp))
    }
}

private val IdCardHeaderMaxWidth = 400.dp
private val IdCardHeaderModifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)

/**
 * Значок режима у имени (визор / лапа): только смотрящему с особым режимом и без маски,
 * себе в своём профиле — тоже (спека §9). Цвет второстепенного текста, ненавязчиво.
 */
@Composable
fun ProfileModeMark(idMode: String?, isMe: Boolean, size: androidx.compose.ui.unit.Dp = 15.dp) {
    val state = LocalIdModeState.current
    val visible = (state.viewerSpecial || (isMe && state.enabled && !state.mask.active)) && IdMode.isSpecial(idMode)
    if (!visible) return
    IdModeGlyph(IdMode.of(idMode), Modifier.padding(start = 6.dp), size = size, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
