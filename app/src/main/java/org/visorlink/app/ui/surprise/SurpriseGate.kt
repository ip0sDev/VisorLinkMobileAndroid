package org.visorlink.app.ui.surprise

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.Surprise
import org.visorlink.app.data.model.SurpriseGiftResult
import org.visorlink.app.data.model.SurpriseSlide
import org.visorlink.app.data.model.SurpriseWish
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.SurpriseRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.idcard.IdCardPerson
import org.visorlink.app.ui.components.idcard.VlIdCard
import org.visorlink.app.ui.components.idcard.rememberCardHaptics
import org.visorlink.app.ui.components.rememberLoopFloat
import org.visorlink.app.ui.components.terminalData
import org.visorlink.app.ui.idcard.LocalIdModeState
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.StealthManager
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Личный сюрприз поверх всего приложения (документ `surprises/{uid}`, его кладёт
 * `scripts/birthday-surprise.cjs` веб-репо): посылка → своя ID-карта и поздравление →
 * переписка в цифрах → пожелания друзей → подарок. Включается по времени из документа,
 * поэтому релиз может уйти заранее. Скрытый режим не пробивает: пока висит «новостное»
 * приложение-обманка, сюрприз ждёт.
 */
@Composable
fun SurpriseGate() {
    val state = LocalIdModeState.current
    val uid = state.myUid
    if (!state.enabled || uid == null) return
    val repo: SurpriseRepository = koinInject()
    val demo by repo.demo.collectAsStateWithLifecycle()
    if (demo) {
        // Демо из настроек разработчика: встроенные данные, подарок не выдаётся, ничего не отмечается
        SurpriseHost(
            surprise = SurpriseDemo.surprise,
            uid = uid,
            claim = {
                delay(900)
                SurpriseDemo.gift(System.currentTimeMillis())
            },
            onOpened = {},
            onFinished = repo::stopDemo,
            onPostpone = repo::stopDemo,
            demo = true,
        )
        return
    }

    val stealth: StealthManager = koinInject()
    val scope = rememberCoroutineScope()
    val surprise by remember(uid) { repo.surpriseFlow(uid) }.collectAsStateWithLifecycle(initialValue = null)
    val s = surprise ?: return
    var seen by remember(uid, s.id) { mutableStateOf(repo.isSeen(uid, s.id)) }
    var postponed by remember(uid, s.id) { mutableStateOf(false) }
    if (seen || s.finished || postponed) return

    // Окно и скрытый режим проверяются раз в секунду: в полночь сюрприз появится сам
    var open by remember(s) { mutableStateOf(s.isOpenAt(System.currentTimeMillis())) }
    var stealthActive by remember { mutableStateOf(stealth.isStealthActive()) }
    var started by rememberSaveable(uid, s.id) { mutableStateOf(false) }
    LaunchedEffect(s) {
        while (true) {
            open = s.isOpenAt(System.currentTimeMillis())
            stealthActive = stealth.isStealthActive()
            delay(1_000)
        }
    }
    // Начатый показ не обрывается концом окна, а вот скрытый режим прячет его всегда
    if (stealthActive || !(open || started)) return
    SideEffect { started = true }

    SurpriseHost(
        surprise = s,
        uid = uid,
        claim = repo::claimGift,
        onOpened = { scope.launch { repo.mark(uid, SurpriseRepository.FIELD_OPENED) } },
        onFinished = {
            repo.markSeen(uid, s.id)
            seen = true
            scope.launch { repo.mark(uid, SurpriseRepository.FIELD_FINISHED) }
        },
        onPostpone = { postponed = true },
    )
}

/** Своя карта и профиль для показа (в маске карта не рисуется — как в профиле). */
@Composable
private fun SurpriseHost(
    surprise: Surprise,
    uid: String,
    claim: suspend () -> SurpriseGiftResult,
    onOpened: () -> Unit,
    onFinished: () -> Unit,
    onPostpone: () -> Unit,
    demo: Boolean = false,
) {
    val state = LocalIdModeState.current
    val cards: IdCardRepository = koinInject()
    val users: UserRepository = koinInject()
    val cardState by remember(uid) { cards.cardFlow(uid) }.collectAsStateWithLifecycle(initialValue = IdCardState.Loading)
    val me by remember(uid) { users.userProfileFlow(uid) }.collectAsStateWithLifecycle(initialValue = null)
    // Подарочная карта рисуется на «носителе», как любой скин: режим, вид и дата регистрации — свои
    val myCard = (cardState as? IdCardState.Ready)?.card
    SurpriseShow(
        surprise = surprise,
        card = if (state.cardsVisible) myCard else null,
        profile = me,
        claim = claim,
        onOpened = onOpened,
        onFinished = onFinished,
        onPostpone = onPostpone,
        demo = demo,
    )
}

private enum class Step { ENVELOPE, CARD, SLIDES, WISHES, GIFT, FINAL }

/** Сам показ без Firebase и Koin-репозиториев — для скриншот-тестов. */
@Composable
internal fun SurpriseShow(
    surprise: Surprise,
    card: IdCard?,
    profile: UserProfile?,
    claim: suspend () -> SurpriseGiftResult,
    onOpened: () -> Unit,
    onFinished: () -> Unit,
    onPostpone: () -> Unit,
    demo: Boolean = false,
) {
    val steps = remember(surprise) {
        buildList {
            add(Step.ENVELOPE)
            add(Step.CARD)
            if (surprise.slides.isNotEmpty()) add(Step.SLIDES)
            if (surprise.wishes.isNotEmpty()) add(Step.WISHES)
            // Подарок не называется заранее: в конце — запечатанная коробка
            add(if (surprise.hasGift) Step.GIFT else Step.FINAL)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    var burst by remember { mutableIntStateOf(0) }
    val haptic = rememberHaptic()
    fun next() {
        if (index < steps.lastIndex) index++ else onFinished()
    }

    // «Назад» на посылке — «потом»: покажется снова при следующем запуске. С подарка назад не уйти:
    // он уже вскрывается на сервере
    BackHandler {
        when {
            index == 0 -> onPostpone()
            step != Step.GIFT -> index--
        }
    }

    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to cs.primary.copy(alpha = 0.16f), 0.7f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * 0.32f),
                        radius = maxOf(size.width, size.height) * 0.75f,
                    ),
                )
            }
            // Под сюрпризом приложение недоступно
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        NightSky(Modifier.fillMaxSize())
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(520)) + scaleIn(tween(620, easing = FastOutSlowInEasing), initialScale = 0.9f)) togetherWith
                    (fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.06f))
            },
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            label = "surprise",
        ) { shown ->
            when (shown) {
                Step.ENVELOPE -> EnvelopeStep(surprise.fromName) {
                    haptic.perform(HapticType.CLICK)
                    onOpened()
                    next()
                }
                Step.CARD -> CardStep(surprise, card, profile, onLanded = { burst++ }, onNext = ::next)
                Step.SLIDES -> SlidesStep(surprise.slides, onDone = ::next)
                Step.WISHES -> WishesStep(surprise.wishes, onNext = ::next)
                Step.GIFT -> GiftStep(
                    surprise = surprise,
                    wearer = card?.let { IdSkinWearer(it.mode, it.species, it.registeredAt) }
                        ?: IdSkinWearer(registeredAt = profile?.createdAt?.toDate()?.time ?: 0),
                    profile = profile,
                    claim = claim,
                    onCelebrate = { burst++ },
                    onDone = onFinished,
                    onLater = onPostpone,
                )
                Step.FINAL -> FinalStep(surprise, onCelebrate = { burst++ }, onDone = onFinished)
            }
        }
        Confetti(burst, Modifier.fillMaxSize())
        if (demo) DemoBadge(onClose = onPostpone, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 20.dp))
    }
}

// ── Шаги ──────────────────────────────────────────────────────────────────────

@Composable
private fun EnvelopeStep(fromName: String?, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(0f) }
    var opening by remember { mutableStateOf(false) }
    fun open() {
        if (opening) return
        opening = true
        // Коробка подпрыгивает и раскрывается, потом — следующий шаг
        scope.launch {
            pop.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            onOpen()
        }
    }
    CenteredColumn {
        Kicker(stringResource(R.string.surprise_delivery_kicker))
        Box(contentAlignment = Alignment.Center) {
            Rays(color = cs.primary, intensity = 0.7f, modifier = Modifier.size(300.dp))
            GiftBox(size = 136.dp, shake = 0f, onClick = ::open, modifier = Modifier.graphicsLayer {
                val p = pop.value
                val sc = 1f + 0.35f * p
                scaleX = sc
                scaleY = sc
                alpha = 1f - p
            })
        }
        Title(stringResource(R.string.surprise_envelope_title))
        fromName?.let { Lead(stringResource(R.string.surprise_envelope_from, it)) }
        VlButton(onClick = ::open, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_open)) }
    }
}

@Composable
private fun CardStep(surprise: Surprise, card: IdCard?, profile: UserProfile?, onLanded: () -> Unit, onNext: () -> Unit) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    val haptics = rememberCardHaptics()
    val drop = remember { Animatable(if (reduceMotion || card == null) 1f else 0f) }
    val landed by remember { derivedStateOf { drop.value >= 1f } }
    val onLandedNow by rememberUpdatedState(onLanded)
    LaunchedEffect(Unit) {
        if (drop.value < 1f) {
            delay(200)
            drop.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        haptics.holo()
        onLandedNow()
    }
    val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: profile?.username?.takeIf { it.isNotBlank() }
    val title = surprise.title
        ?: name?.let { stringResource(R.string.surprise_title_named, it) }
        ?: stringResource(R.string.surprise_title_default)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = min(320.dp, maxWidth * 0.82f)
        CenteredColumn {
            if (card != null && profile != null) {
                val appearance = ProfileAppearance.resolve(owner = profile, viewer = profile)
                VlIdCard(
                    card = card,
                    person = IdCardPerson(profile.displayName, profile.username, profile.avatarUrl),
                    width = cardWidth,
                    accent = appearance.accentHexColor ?: appearance.accent?.seedColor,
                    emojis = appearance.emojis.orEmpty(),
                    interactive = landed,
                    modifier = Modifier.graphicsLayer {
                        // Падает сверху, на лету делает оборот и ложится ровно
                        val p = drop.value
                        alpha = (p * 1.8f).coerceAtMost(1f)
                        translationY = -200.dp.toPx() * (1f - p)
                        rotationZ = -14f * (1f - p)
                        rotationY = 360f * (1f - p)
                        cameraDistance = 14f * density
                        val sc = 0.65f + 0.35f * p
                        scaleX = sc
                        scaleY = sc
                    },
                )
            }
            Title(title)
            surprise.message?.let { Typewriter(it, start = landed) }
            surprise.fromName?.let {
                Text(
                    stringResource(R.string.surprise_signature, it),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VlButton(onClick = onNext, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_next)) }
        }
    }
}

@Composable
private fun SlidesStep(slides: List<SurpriseSlide>, onDone: () -> Unit) {
    var i by rememberSaveable { mutableIntStateOf(0) }
    val haptic = rememberHaptic()
    val done by rememberUpdatedState(onDone)
    BackHandler(enabled = i > 0) { i-- }
    val cs = MaterialTheme.colorScheme
    val shape = VlTheme.tokens.shapes.rounded(2.dp)
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(slides) {
                // Как в историях: левая треть — назад, остальное — дальше
                detectTapGestures { o ->
                    if (o.x < size.width / 3f) {
                        if (i > 0) i--
                    } else if (i < slides.lastIndex) {
                        i++
                        haptic.perform(HapticType.SELECTION)
                    } else {
                        done()
                    }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            slides.indices.forEach { k ->
                // Текущий сегмент заполняется слева направо, пройденные — целиком
                val fill by animateFloatAsState(if (k <= i) 1f else 0f, tween(if (k == i) 520 else 200), label = "seg")
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(shape)
                        .background(cs.onSurface.copy(alpha = 0.15f))
                        .drawBehind { drawRect(cs.primary, size = Size(size.width * fill, size.height)) },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = i,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(340)) + slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { w -> dir * w / 4 }) togetherWith
                        (fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { w -> -dir * w / 6 })
                },
                label = "slide",
            ) { idx -> SlideView(slides[idx]) }
        }
        Text(
            stringResource(R.string.surprise_tap_hint),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun SlideView(slide: SurpriseSlide) {
    val cs = MaterialTheme.colorScheme
    val reduceMotion = VlTheme.tokens.reduceMotion
    // Главное число «выпрыгивает» с перелётом, подписи подтягиваются следом
    val pop = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    val rise = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(160)
        rise.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        slide.imageUrl?.let {
            CachedImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.size(150.dp).graphicsLayer {
                    val p = pop.value
                    scaleX = p
                    scaleY = p
                    rotationZ = -10f * (1f - p)
                },
            )
        }
        slide.kicker?.let { Kicker(it) }
        slide.value?.let {
            Box(Modifier.graphicsLayer {
                val p = pop.value
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
                alpha = p.coerceIn(0f, 1f)
            }) {
                CountUpText(
                    value = it,
                    countUp = slide.countUp,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold).terminalData(),
                    color = cs.primary,
                )
            }
        }
        Column(
            Modifier.graphicsLayer {
                alpha = rise.value
                translationY = 18.dp.toPx() * (1f - rise.value)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            slide.text?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, color = cs.onSurface, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp))
            }
            slide.caption?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
            }
        }
    }
}

@Composable
private fun WishesStep(wishes: List<SurpriseWish>, onNext: () -> Unit) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    var visible by rememberSaveable { mutableIntStateOf(if (reduceMotion) wishes.size else 0) }
    LaunchedEffect(Unit) {
        while (visible < wishes.size) {
            delay(380)
            visible++
        }
    }
    val cs = MaterialTheme.colorScheme
    CenteredColumn(spacing = 12.dp) {
        Kicker(stringResource(R.string.surprise_wishes_kicker))
        Title(stringResource(R.string.surprise_wishes_title))
        wishes.forEachIndexed { idx, w ->
            AnimatedVisibility(
                visible = idx < visible,
                enter = fadeIn(tween(420)) + slideInVertically(tween(480, easing = FastOutSlowInEasing)) { it / 2 } +
                    scaleIn(tween(480), initialScale = 0.94f),
            ) {
                VlSurface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (w.name.isNotEmpty()) Text(w.name, style = MaterialTheme.typography.titleSmall, color = cs.primary)
                        Text(w.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                }
            }
        }
        VlButton(onClick = onNext, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_next)) }
    }
}

/** Финал без подарка: только поздравление. */
@Composable
private fun FinalStep(surprise: Surprise, onCelebrate: () -> Unit, onDone: () -> Unit) {
    val haptics = rememberCardHaptics()
    val celebrate by rememberUpdatedState(onCelebrate)
    LaunchedEffect(Unit) {
        celebrate()
        haptics.success()
    }
    val cs = MaterialTheme.colorScheme
    val pulse by rememberLoopFloat(enabled = !VlTheme.tokens.reduceMotion) {
        rememberInfiniteTransition(label = "final").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "final",
        )
    }
    CenteredColumn {
        Icon(
            Icons.Default.Celebration,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(84.dp).graphicsLayer { rotationZ = (pulse - 0.5f) * 14f },
        )
        Title(surprise.giftTitle ?: stringResource(R.string.surprise_final_default))
        surprise.giftText?.let { Lead(it) }
        surprise.fromName?.let {
            Text(stringResource(R.string.surprise_signature, it), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        VlButton(onClick = onDone, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.surprise_thanks)) }
    }
}

// ── Общие куски ───────────────────────────────────────────────────────────────

@Composable
internal fun CenteredColumn(spacing: androidx.compose.ui.unit.Dp = 18.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
internal fun Kicker(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.Center,
)

@Composable
internal fun Title(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 440.dp),
)

@Composable
internal fun Lead(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 420.dp),
)

/**
 * Текст печатается по символу (по кодовой точке — эмодзи не рвутся). Ненапечатанный хвост
 * прозрачный, а не отсутствует, чтобы абзац не прыгал по мере печати. Тап — допечатать сразу.
 */
@Composable
private fun Typewriter(text: String, start: Boolean) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    var shown by rememberSaveable(text) { mutableIntStateOf(if (reduceMotion) text.length else 0) }
    LaunchedEffect(text, start) {
        if (!start) return@LaunchedEffect
        while (shown < text.length) {
            val ch = text[shown]
            delay(if (ch == '\n' || ch == '.' || ch == '!' || ch == '?') 240 else 30)
            shown = (shown + Character.charCount(text.codePointAt(shown))).coerceAtMost(text.length)
        }
    }
    Text(
        buildAnnotatedString {
            append(text.substring(0, shown))
            withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(shown)) }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { shown = text.length },
    )
}

private val NUMBER = Regex("""^(\D*?)(\d[\d   ]*\d|\d)(\D*)$""")

/** «1 284 сообщения» — число набегает от нуля, приставка и хвост остаются как есть. */
@Composable
internal fun CountUpText(value: String, countUp: Boolean, style: TextStyle, color: Color, align: TextAlign = TextAlign.Center) {
    val match = remember(value) { if (countUp) NUMBER.matchEntire(value) else null }
    val target = remember(match) { match?.groupValues?.get(2)?.filter(Char::isDigit)?.toLongOrNull() }
    if (match == null || target == null) {
        Text(value, style = style, color = color, textAlign = align)
        return
    }
    val reduceMotion = VlTheme.tokens.reduceMotion
    val progress = remember(value) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(value) { progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    val grouped = match.groupValues[2].any { !it.isDigit() }
    val current = (target * progress.value).roundToLong()
    Text(
        match.groupValues[1] + formatNumber(current, grouped) + match.groupValues[3],
        style = style,
        color = color,
        textAlign = align,
    )
}

/** Разряды через узкий неразрывный пробел, как пишут в русском тексте. */
internal fun formatNumber(n: Long, grouped: Boolean): String {
    val digits = n.toString()
    if (!grouped || digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString(" ").reversed()
}

// ── Конфетти ──────────────────────────────────────────────────────────────────

private const val CONFETTI_MS = 3400

private class ConfettiPiece(
    val vx: Float,
    val vy: Float,
    val sizeDp: Float,
    val aspect: Float,
    val rotation: Float,
    val spin: Float,
    val flutter: Float,
    val color: Int,
    val round: Boolean,
)

/**
 * Залп конфетти из точки над центром: разлёт с сопротивлением воздуха, потом падение, бумажки
 * «переворачиваются» (ширина по cos). Каждое увеличение [trigger] — новый залп. Цвета —
 * сигнальные цвета темы. Без анимаций системы не рисуется вовсе.
 */
@Composable
internal fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0 || VlTheme.tokens.reduceMotion) return
    val cs = MaterialTheme.colorScheme
    val status = VlTheme.tokens.status
    val colors = listOf(cs.primary, cs.secondary, cs.tertiary, status.success, status.warning, cs.error)
    val pieces = remember(trigger) {
        val r = Random(trigger * 7919)
        List(120) {
            ConfettiPiece(
                vx = r.nextFloat() * 2f - 1f,
                vy = -(0.45f + r.nextFloat() * 0.75f),
                sizeDp = 6f + r.nextFloat() * 6f,
                aspect = 0.45f + r.nextFloat() * 0.6f,
                rotation = r.nextFloat() * 360f,
                spin = (r.nextFloat() * 2f - 1f) * 540f,
                flutter = 4f + r.nextFloat() * 9f,
                color = r.nextInt(colors.size),
                round = r.nextInt(5) == 0,
            )
        }
    }
    val time = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { time.animateTo(1f, tween(CONFETTI_MS, easing = LinearEasing)) }
    Canvas(modifier) {
        val p = time.value
        if (p <= 0f || p >= 1f) return@Canvas
        val t = p * CONFETTI_MS / 1000f
        val k = 1.6f
        val drag = (1f - exp(-k * t)) / k
        val fall = 0.22f * size.height * t * t
        val alpha = if (p > 0.75f) (1f - p) / 0.25f else 1f
        val cx = size.width / 2f
        val cy = size.height * 0.36f
        pieces.forEach { c ->
            val x = cx + c.vx * size.width * 0.95f * drag
            val y = cy + c.vy * size.height * 0.9f * drag + fall
            val w = c.sizeDp * density
            val h = w * c.aspect
            val color = colors[c.color].copy(alpha = alpha)
            rotate(c.rotation + c.spin * t, pivot = Offset(x, y)) {
                if (c.round) {
                    drawCircle(color, radius = w * 0.38f, center = Offset(x, y))
                } else {
                    val fw = w * abs(cos(c.flutter * t)).coerceAtLeast(0.12f)
                    drawRect(color, topLeft = Offset(x - fw / 2f, y - h / 2f), size = Size(fw, h))
                }
            }
        }
    }
}
