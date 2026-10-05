package org.visorlink.app.ui.idcard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardTraits
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.IdModeGlyph
import org.visorlink.app.ui.components.idcard.VlIdCard
import org.visorlink.app.ui.theme.VlTheme

/** Длительность церемонии: совпадает с задержками анимаций карты (CEREMONY_MS в вебе). */
private const val CEREMONY_S = 6.2f

/** Карта профиля для превью: акцент HEX или пресет, эмодзи (веб: resolveProfileAccent). */
internal fun UserProfile.idCardPerson() = IdCardPerson(displayName, username, avatarUrl)
internal fun UserProfile.idCardAccent() = ProfileAppearance.parse(customization).let { it.accentHexColor ?: it.accent?.seedColor }
internal fun UserProfile.idCardEmojis() = (customization["emojis"] as? String).orEmpty()

/** Дата регистрации: users.createdAt, у старых аккаунтов — время создания в Firebase Auth. */
internal fun UserProfile.registeredAtMs(): Long =
    createdAt?.toDate()?.time ?: FirebaseAuth.getInstance().currentUser?.metadata?.creationTimestamp ?: System.currentTimeMillis()

/**
 * Выдача ID-карты тем, у кого её нет (новые и все существующие пользователи) — спека §5.
 * Показ держится до «Готово»: карта придёт в подписке посреди церемонии, экран не закрывается.
 */
@Composable
fun IdCardGate() {
    val state = LocalIdModeState.current
    val uid = state.myUid
    if (!state.enabled || uid == null) return
    val repo: IdCardRepository = koinInject()
    val users: UserRepository = koinInject()
    val cardState by remember(uid) { repo.cardFlow(uid) }.collectAsState(initial = IdCardState.Loading)
    val me by remember(uid) { users.userProfileFlow(uid) }.collectAsState(initial = null)
    var showingFor by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(cardState, uid) {
        if (cardState == IdCardState.Ready(null)) showingFor = uid
    }
    if (showingFor != uid) return
    val profile = me ?: return
    IdCardIssuance(profile = profile, onIssued = { showingFor = null })
}

private enum class IssueStep { INTRO, SPECIAL, CHOOSE, PRINTING, DONE }

private fun previewCard(mode: IdMode, species: String?, registeredAt: Long) = IdCard(
    mode = mode,
    species = species,
    serial = "VL-••••-••••",
    traits = IdCardTraits(),
    issuedAt = System.currentTimeMillis(),
    registeredAt = registeredAt,
)

/**
 * Основной экран — только Standard: особые режимы спрятаны и открываются целенаправленно
 * (ссылка → нейтральное подтверждение → выбор), чтобы не-фурри не встретили их случайно (§10).
 * `issueIdCard` вызывается до анимации — церемония показывает настоящие признаки и серийник.
 */
@Composable
fun IdCardIssuance(profile: UserProfile, onIssued: () -> Unit) {
    val repo: IdCardRepository = koinInject()
    val scope = rememberCoroutineScope()
    val reduceMotion = VlTheme.tokens.reduceMotion
    var step by rememberSaveable { mutableStateOf(IssueStep.INTRO) }
    var mode by rememberSaveable { mutableStateOf(IdMode.PROTOGEN) }
    var species by rememberSaveable { mutableStateOf("") }
    var card by remember { mutableStateOf<IdCard?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val issueError = stringResource(R.string.idcard_issue_error)
    val registeredAt = remember(profile.createdAt) { profile.registeredAtMs() }
    val person = profile.idCardPerson()
    val accent = profile.idCardAccent()
    val emojis = profile.idCardEmojis()

    // В приложение без карты не пускаем: «назад» на первом шаге ничего не делает
    BackHandler {
        when (step) {
            IssueStep.SPECIAL, IssueStep.CHOOSE -> step = IssueStep.INTRO
            else -> Unit
        }
    }

    fun issue(chosen: IdMode) {
        busy = true
        error = null
        scope.launch {
            try {
                card = repo.issue(chosen, if (chosen == IdMode.STANDARD) null else species.trim().ifEmpty { null })
                step = IssueStep.PRINTING
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: issueError
            } finally {
                busy = false
            }
        }
    }

    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .drawBehind {
                // radial-gradient(ellipse at 50% 30%, primary 10% поверх фона, фон 70%)
                drawRect(
                    Brush.radialGradient(
                        0f to cs.primary.copy(alpha = 0.1f).compositeOverBg(cs.background), 0.7f to cs.background,
                        center = Offset(size.width / 2f, size.height * 0.3f),
                        radius = maxOf(size.width, size.height) * 0.75f,
                    ),
                )
            }
            // Перехватывает касания: под выдачей приложение недоступно
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
    ) {
        val cardWidth = min(320.dp, maxWidth * 0.78f)
        val ceremonyWidth = min(360.dp, maxWidth * 0.84f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            when (step) {
                IssueStep.INTRO -> {
                    Text("VISORLINK", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.6.sp), color = cs.primary)
                    Title(stringResource(R.string.idcard_issue_title))
                    Lead(stringResource(R.string.idcard_issue_lead))
                    VlIdCard(previewCard(IdMode.STANDARD, null, registeredAt), person, width = cardWidth, interactive = false)
                    error?.let { ErrorText(it) }
                    VlButton(onClick = { issue(IdMode.STANDARD) }, enabled = !busy, modifier = Modifier.widthIn(min = 220.dp)) {
                        Text(stringResource(if (busy) R.string.idcard_issue_issuing else R.string.idcard_issue_get))
                    }
                    QuietLink(stringResource(R.string.idcard_issue_other_type)) { step = IssueStep.SPECIAL }
                }

                IssueStep.SPECIAL -> {
                    Title(stringResource(R.string.idcard_special_title))
                    Lead(stringResource(R.string.idcard_special_text))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VlDialogButton(onClick = { step = IssueStep.INTRO }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(15.dp))
                                Text(stringResource(R.string.idcard_back))
                            }
                        }
                        VlButton(onClick = { step = IssueStep.CHOOSE }) { Text(stringResource(R.string.idcard_special_show)) }
                    }
                }

                IssueStep.CHOOSE -> {
                    Title(stringResource(R.string.idcard_choose_title))
                    Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(IdMode.PROTOGEN, IdMode.BEAST).forEach { m ->
                            ModeOption(
                                mode = m,
                                desc = stringResource(if (m == IdMode.PROTOGEN) R.string.idcard_choose_protogen else R.string.idcard_choose_beast),
                                selected = mode == m,
                                onClick = { mode = m },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    VlIdCard(
                        previewCard(mode, species.trim().ifEmpty { null }, registeredAt), person,
                        width = cardWidth, accent = accent, emojis = emojis, interactive = false,
                    )
                    SpeciesField(mode, species, { species = it.take(40) }, Modifier.fillMaxWidth().widthIn(max = 520.dp))
                    error?.let { ErrorText(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VlDialogButton(onClick = { step = IssueStep.INTRO }) { Text(stringResource(R.string.idcard_choose_back_to_standard)) }
                        VlButton(onClick = { issue(mode) }, enabled = !busy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp))
                                Text(stringResource(if (busy) R.string.idcard_issue_issuing else R.string.idcard_choose_issue))
                            }
                        }
                    }
                }

                IssueStep.PRINTING, IssueStep.DONE -> card?.let { issued ->
                    Ceremony(
                        card = issued,
                        person = person,
                        accent = accent,
                        emojis = emojis,
                        width = ceremonyWidth,
                        done = step == IssueStep.DONE,
                        reduceMotion = reduceMotion,
                        onFinished = { step = IssueStep.DONE },
                        onContinue = onIssued,
                    )
                }
            }
        }
    }
}

private fun Color.compositeOverBg(bg: Color) = androidx.compose.ui.graphics.lerp(bg, this.copy(alpha = 1f), this.alpha)

@Composable
private fun Title(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
)

@Composable
private fun Lead(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 420.dp),
)

@Composable
private fun ErrorText(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)

/** Неприметная ссылка — особые режимы ищут целенаправленно. */
@Composable
internal fun QuietLink(text: String, onClick: () -> Unit) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
    modifier = Modifier
        .clip(VlTheme.tokens.shapes.chip)
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp),
)

@Composable
internal fun ModeOption(mode: IdMode, desc: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    VlSurface(
        modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        overrideColor = if (selected) VlTheme.tokens.selectionFill else null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = if (enabled) onClick else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdModeGlyph(mode, size = 16.dp, color = if (selected) cs.primary else cs.onSurface)
                Text(
                    org.visorlink.app.ui.components.idcard.modeLabel(mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) cs.primary else cs.onSurface,
                )
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SpeciesField(mode: IdMode, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val label = stringResource(if (mode == IdMode.PROTOGEN) R.string.idcard_field_model else R.string.idcard_field_species) +
        " " + stringResource(R.string.idcard_choose_optional)
    VlTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        placeholder = stringResource(if (mode == IdMode.PROTOGEN) R.string.idcard_choose_model_placeholder else R.string.idcard_choose_species_placeholder),
        modifier = modifier,
        trailing = trailing,
    )
}

/**
 * Церемония ≈ 6 с, можно пропустить: принтер, узор прорисовывается, фото проявляется, поля
 * печатаются, штамп, ламинатор, голограмма вспыхивает → «Карта выдана» → «Готово».
 */
@Composable
private fun Ceremony(
    card: IdCard,
    person: IdCardPerson,
    accent: Color?,
    emojis: String,
    width: Dp,
    done: Boolean,
    reduceMotion: Boolean,
    onFinished: () -> Unit,
    onContinue: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val time = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) return@LaunchedEffect
        if (reduceMotion) {
            delay(300)
        } else {
            time.animateTo(CEREMONY_S, tween((CEREMONY_S * 1000).toInt(), easing = LinearEasing))
        }
        onFinished()
    }
    // Хаптика церемонии — по тем же отметкам времени, что и анимации карты
    val haptics = org.visorlink.app.ui.components.idcard.rememberCardHaptics()
    LaunchedEffect(Unit) {
        val fired = HashSet<String>()
        androidx.compose.runtime.snapshotFlow { time.value }.collect { t ->
            fun at(key: String, sec: Float, block: () -> Unit) { if (t >= sec && fired.add(key)) block() }
            // Протяжка принтером: щелчки каждые 0.12 с, пока карта выезжает
            var i = 0
            while (i * 0.12f < 1.4f) { val k = i; at("p$k", k * 0.12f) { haptics.printerStep() }; i++ }
            at("stamp", 3.62f) { haptics.stamp() }
            if (card.traits.laminated) at("lam", 4.4f) { haptics.laminate() }
            if (card.traits.foil != org.visorlink.app.data.idcard.IdFoil.NONE) at("holo", 5.0f) { haptics.holo() }
        }
    }
    LaunchedEffect(done) { if (done) haptics.success() }

    // Карта выезжает из щели принтера (idi-print 1.4 с), после — «в руке»
    val printOut = if (done || reduceMotion) 1f else (time.value / 1.4f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
    val lift by animateFloatAsState(if (done) 1f else 0f, tween(600), label = "lift")
    val printerAlpha by animateFloatAsState(if (done) 0f else 1f, tween(500), label = "printer")
    val ledBlink = if (done) 1f else if (((time.value * 1000).toInt() / 250) % 2 == 0) 1f else 0.2f

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(contentAlignment = Alignment.TopCenter) {
            // Принтер
            Box(
                Modifier
                    .graphicsLayer { alpha = printerAlpha; translationY = -24.dp.toPx() * (1f - printerAlpha) }
                    .size(width + 40.dp, 22.dp)
                    .clip(VlTheme.tokens.shapes.rounded(12.dp))
                    .background(cs.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .fillMaxWidth(0.88f)
                        .height(4.dp)
                        .clip(VlTheme.tokens.shapes.rounded(2.dp))
                        .background(cs.background),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 12.dp)
                        .size(6.dp)
                        .clip(VlTheme.tokens.shapes.indicator)
                        .background(VlTheme.tokens.status.success.copy(alpha = ledBlink)),
                )
            }
            VlIdCard(
                card = card,
                person = person,
                width = width,
                accent = accent,
                emojis = emojis,
                interactive = done,
                issueTime = if (done || reduceMotion) null else time.value,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        translationY = -size.height * 0.55f * (1f - printOut) - 10.dp.toPx() * lift
                    }
                    .drawWithContent {
                        // Из щели видна только выехавшая часть
                        clipRect(top = size.height * 0.55f * (1f - printOut)) { this@drawWithContent.drawContent() }
                    },
            )
        }
        Column(Modifier.heightIn(min = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!done) {
                QuietLink(stringResource(R.string.idcard_issue_skip), onFinished)
            } else {
                Title(stringResource(R.string.idcard_issue_done))
                Text(card.serial, style = VlTheme.tokens.data.dataSmall.copy(letterSpacing = 1.5.sp), color = cs.onSurfaceVariant)
                VlButton(onClick = onContinue, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.idcard_issue_continue)) }
            }
        }
    }
}
