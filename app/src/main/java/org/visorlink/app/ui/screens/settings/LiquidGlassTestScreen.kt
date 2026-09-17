package org.visorlink.app.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.visorlink.app.R
import org.visorlink.app.ui.components.NeumorphicLiquidBridge
import org.visorlink.app.ui.components.NeumorphicLiquidSegmentedControl
import org.visorlink.app.ui.components.NeumorphicMetaballsCanvas
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.calculateJellyScale
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DynamicBarMode {
    HIDDEN,   // Состояние до появления "из ниоткуда"
    ISLAND,   // Компактный динамический остров (pill)
    EXPANDED, // Панель с поиском
    FULL_BAR  // Полноразмерный заголовок с действиями
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassTestScreen(
    onBack: () -> Unit
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val structure = tokens.structure
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Состояния физики и контролов
    var surfaceTension by remember { mutableFloatStateOf(1.0f) }
    var bounciness by remember { mutableFloatStateOf(0.65f) }

    // Состояние табов демо-витрины
    val demoTabs = listOf("Остров", "Карточки", "Капли", "Таббар")
    var selectedDemoTab by remember { mutableIntStateOf(0) }

    // ── Состояния секции 1: Dynamic Island ──
    var barMode by remember { mutableStateOf(DynamicBarMode.ISLAND) }
    val barScaleAnim = remember { Animatable(1f) }
    val barJellyAnim = remember { Animatable(0f) }

    val barWidthDp by animateDpAsState(
        targetValue = when (barMode) {
            DynamicBarMode.HIDDEN -> 16.dp
            DynamicBarMode.ISLAND -> 160.dp
            DynamicBarMode.EXPANDED -> 280.dp
            DynamicBarMode.FULL_BAR -> 340.dp
        },
        animationSpec = spring(
            dampingRatio = bounciness,
            stiffness = Spring.StiffnessMedium
        ),
        label = "barWidth"
    )

    val barHeightDp by animateDpAsState(
        targetValue = when (barMode) {
            DynamicBarMode.HIDDEN -> 16.dp
            DynamicBarMode.ISLAND -> 46.dp
            DynamicBarMode.EXPANDED -> 56.dp
            DynamicBarMode.FULL_BAR -> 88.dp
        },
        animationSpec = spring(
            dampingRatio = bounciness * 0.9f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "barHeight"
    )

    // Функция появления из ниоткуда (рождение из компактной капли)
    val triggerSpawnFromNothing: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            barMode = DynamicBarMode.HIDDEN
            barScaleAnim.snapTo(0.01f)
            barJellyAnim.snapTo(1f)
            delay(20L)
            barMode = DynamicBarMode.ISLAND
            launch {
                barScaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                barJellyAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.45f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    // ── Состояния секции 2: Выливание карточек (Отпочковывание с истончением перешейка и баунсом) ──
    val cardCount = 3
    var isCardsPoured by remember { mutableStateOf(false) }
    // Прогресс выливания карточки вниз (0f -> 1f)
    val cardProgresses = remember { List(cardCount) { Animatable(0f) } }
    // Осциллятор сочного желейного баунса (Squash & Stretch)
    val cardJellyAnims = remember { List(cardCount) { Animatable(0f) } }
    // Защелка разрыва перешейка: предотвращает мерцание при упругих отскоках
    val cardSnapped = remember { List(cardCount) { mutableStateOf(false) } }
    // Отдача родительского элемента вверх при отрыве капли
    val parentRecoils = remember { List(cardCount) { Animatable(0f) } }
    // Анимация упругой ретракции хвостиков после разрыва нити
    val cardRetractAnims = remember { List(cardCount) { Animatable(0f) } }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    // Вязкий органический карв для выливания тягучей жидкости
    val PourEasing = CubicBezierEasing(0.60f, 0.0f, 0.25f, 1f)

    // --- Интерактивные параметры настройки мостика (липкость и геометрия) ---
    var bridgeBaseWidthDp by remember { mutableFloatStateOf(160f) } // 60..240 dp (ширина основания мостика)
    var bridgeStretchGapDp by remember { mutableFloatStateOf(20f) } // 8..40 dp (дистанция растяжения перешейка)
    var minWaistDp by remember { mutableFloatStateOf(14f) } // 4..30 dp (толщина струйки перед отрывом)
    var cardGapDp by remember { mutableFloatStateOf(16f) } // 8..32 dp (зазор между карточками в покое)

    // --- Интерактивные параметры желейности и физики (Squash & Stretch) ---
    var jellySquashImpulse by remember { mutableFloatStateOf(0.32f) } // 0.10..0.55 (сила сплющивания при приземлении)
    var jellyFlightStretch by remember { mutableFloatStateOf(0.18f) } // 0.05..0.35 (растяжение в полете)
    var jellyDamping by remember { mutableFloatStateOf(0.28f) } // 0.15..0.75 (демпфирование желе, меньше = живее)
    var jellyStiffness by remember { mutableFloatStateOf(180f) } // 60..450 (частота пружины желе)
    var parentRecoilDp by remember { mutableFloatStateOf(8f) } // 0..18 dp (отдача тулбара вверх)
    var pourDurationMs by remember { mutableIntStateOf(320) } // 180..600 ms (скорость выливания)

    val pourCards: () -> Unit = {
        isCardsPoured = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            for (i in 0 until cardCount) {
                cardSnapped[i].value = false
                cardProgresses[i].snapTo(0f)
                cardJellyAnims[i].snapTo(0f)
                parentRecoils[i].snapTo(0f)
                cardRetractAnims[i].snapTo(0f)
            }

            val stepDelay = pourDurationMs.toLong()

            // === КАРТОЧКА 0: Выливается напрямую из тулбара ===
            val targetTop0 = 52f + cardGapDp
            val startTop0 = 52f - 56f
            launch {
                launch {
                    cardJellyAnims[0].animateTo(jellyFlightStretch, tween((pourDurationMs * 0.85f).toInt()))
                }
                cardProgresses[0].animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = pourDurationMs, easing = PourEasing)
                ) {
                    val toolbarBottom = 52f + parentRecoils[0].value
                    val cardTop = startTop0 + (targetTop0 - startTop0) * value
                    val currentGap = cardTop - toolbarBottom
                    // Геометрический снап: ровно при превышении порога натяжения
                    if (!cardSnapped[0].value && currentGap >= bridgeStretchGapDp) {
                        cardSnapped[0].value = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        launch {
                            cardRetractAnims[0].snapTo(1f)
                            cardRetractAnims[0].animateTo(0f, spring(0.40f, 380f))
                        }
                        launch {
                            parentRecoils[0].snapTo(-parentRecoilDp)
                            parentRecoils[0].animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f))
                        }
                    }
                }
                // Гарантированный снап по приземлению, если зазор был меньше порога
                if (!cardSnapped[0].value) {
                    cardSnapped[0].value = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    launch {
                        cardRetractAnims[0].snapTo(1f)
                        cardRetractAnims[0].animateTo(0f, spring(0.40f, 380f))
                    }
                    launch {
                        parentRecoils[0].snapTo(-parentRecoilDp)
                        parentRecoils[0].animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f))
                    }
                }
                // Сочный желейный баунс о пол
                launch {
                    cardJellyAnims[0].snapTo(-jellySquashImpulse)
                    cardJellyAnims[0].animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = jellyDamping, stiffness = jellyStiffness)
                    )
                }
            }

            // === КАРТОЧКА 1: Выливается из Карточки 0 ===
            delay(stepDelay)
            val targetTop1 = targetTop0 + 64f + cardGapDp
            val startTop1 = (targetTop0 + 64f) - 56f
            launch {
                launch {
                    cardJellyAnims[1].animateTo(jellyFlightStretch, tween((pourDurationMs * 0.85f).toInt()))
                }
                cardProgresses[1].animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = pourDurationMs, easing = PourEasing)
                ) {
                    val card0Bottom = targetTop0 + 64f + parentRecoils[1].value
                    val cardTop = startTop1 + (targetTop1 - startTop1) * value
                    val currentGap = cardTop - card0Bottom
                    if (!cardSnapped[1].value && currentGap >= bridgeStretchGapDp) {
                        cardSnapped[1].value = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        launch {
                            cardRetractAnims[1].snapTo(1f)
                            cardRetractAnims[1].animateTo(0f, spring(0.40f, 380f))
                        }
                        launch {
                            parentRecoils[1].snapTo(-parentRecoilDp)
                            parentRecoils[1].animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f))
                        }
                    }
                }
                if (!cardSnapped[1].value) {
                    cardSnapped[1].value = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    launch {
                        cardRetractAnims[1].snapTo(1f)
                        cardRetractAnims[1].animateTo(0f, spring(0.40f, 380f))
                    }
                    launch {
                        parentRecoils[1].snapTo(-parentRecoilDp)
                        parentRecoils[1].animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f))
                    }
                }
                launch {
                    cardJellyAnims[1].snapTo(-jellySquashImpulse)
                    cardJellyAnims[1].animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = jellyDamping, stiffness = jellyStiffness)
                    )
                }
            }

            // === КАРТОЧКА 2: Выливается из Карточки 1 ===
            delay(stepDelay)
            val targetTop2 = targetTop1 + 64f + cardGapDp
            val startTop2 = (targetTop1 + 64f) - 56f
            launch {
                launch {
                    cardJellyAnims[2].animateTo(jellyFlightStretch, tween((pourDurationMs * 0.85f).toInt()))
                }
                cardProgresses[2].animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = pourDurationMs, easing = PourEasing)
                ) {
                    val card1Bottom = targetTop1 + 64f + parentRecoils[2].value
                    val cardTop = startTop2 + (targetTop2 - startTop2) * value
                    val currentGap = cardTop - card1Bottom
                    if (!cardSnapped[2].value && currentGap >= bridgeStretchGapDp) {
                        cardSnapped[2].value = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        launch {
                            cardRetractAnims[2].snapTo(1f)
                            cardRetractAnims[2].animateTo(0f, spring(0.40f, 380f))
                        }
                        launch {
                            parentRecoils[2].snapTo(-parentRecoilDp)
                            parentRecoils[2].animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f))
                        }
                    }
                }
                if (!cardSnapped[2].value) {
                    cardSnapped[2].value = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    launch {
                        cardRetractAnims[2].snapTo(1f)
                        cardRetractAnims[2].animateTo(0f, spring(0.40f, 380f))
                    }
                    launch {
                        parentRecoils[2].snapTo(-parentRecoilDp)
                        parentRecoils[2].animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f))
                    }
                }
                launch {
                    cardJellyAnims[2].snapTo(-jellySquashImpulse)
                    cardJellyAnims[2].animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = jellyDamping, stiffness = jellyStiffness)
                    )
                }
            }
        }
    }

    val recallCards: () -> Unit = {
        isCardsPoured = false
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        coroutineScope.launch {
            for (i in 0 until cardCount) {
                cardRetractAnims[i].snapTo(0f)
            }
            // Карточки складываются каскадно снизу вверх: 2 -> 1 -> 0
            launch {
                cardJellyAnims[2].animateTo(0.06f, tween(80))
                cardProgresses[2].animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                cardJellyAnims[2].snapTo(0f)
            }
            delay(70L)
            launch {
                cardJellyAnims[1].animateTo(0.06f, tween(80))
                cardProgresses[1].animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                cardJellyAnims[1].snapTo(0f)
            }
            delay(70L)
            launch {
                cardJellyAnims[0].animateTo(0.06f, tween(80))
                cardProgresses[0].animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                cardJellyAnims[0].snapTo(0f)
                // Финальный гидроудар поглощения в тулбар
                parentRecoils[0].snapTo(parentRecoilDp * 0.75f)
                parentRecoils[0].animateTo(0f, spring(dampingRatio = 0.48f, stiffness = 320f))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // ── Состояния секции 3: Интерактивные метаболлы ──
    val dropCenterAX = with(density) { 100.dp.toPx() }
    val dropCenterAY = with(density) { 130.dp.toPx() }
    val dropCenterBX = with(density) { 230.dp.toPx() }
    val dropCenterBY = with(density) { 130.dp.toPx() }
    val dropRadiusPx = with(density) { 34.dp.toPx() }
    val maxMetaballDistPx = with(density) { 120.dp.toPx() }

    var dropACenter by remember(density) { mutableStateOf(Offset(dropCenterAX, dropCenterAY)) }
    var dropBCenter by remember(density) { mutableStateOf(Offset(dropCenterBX, dropCenterBY)) }
    val dropWobbleAnim = remember { Animatable(0f) }
    var wasConnected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            VlTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.animation_test_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(cs.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Переключатель вкладок витрины
            NeumorphicLiquidSegmentedControl(
                tabs = demoTabs,
                selectedIndex = selectedDemoTab,
                onTabSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedDemoTab = it
                }
            )

            // ── Вкладка 0: Dynamic Island & Neumorphic Bar ──
            if (selectedDemoTab == 0) {
                VlCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Органический морфинг тулбара",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            text = "Появление из ниоткуда, squash & stretch и переключение состояний",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )

                        // Холст демонстрации острова
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .vlInset(structure, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(cs.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            val jelly = barJellyAnim.value
                            val (scaleX, scaleY) = calculateJellyScale(jelly)
                            val barShape = RoundedCornerShape((barHeightDp / 2).coerceAtLeast(16.dp))

                            Box(
                                modifier = Modifier
                                    .size(width = barWidthDp, height = barHeightDp)
                                    .graphicsLayer {
                                        this.scaleX = barScaleAnim.value * scaleX
                                        this.scaleY = barScaleAnim.value * scaleY
                                        this.alpha = if (barScaleAnim.value < 0.05f) 0f else 1f
                                    }
                                    .vlRaised(structure, barShape)
                                    .clip(barShape)
                                    .background(cs.surfaceContainerHighest)
                                    .vlHairline(color = cs.outlineVariant, shape = barShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        barMode = when (barMode) {
                                            DynamicBarMode.ISLAND -> DynamicBarMode.EXPANDED
                                            DynamicBarMode.EXPANDED -> DynamicBarMode.FULL_BAR
                                            DynamicBarMode.FULL_BAR -> DynamicBarMode.ISLAND
                                            DynamicBarMode.HIDDEN -> DynamicBarMode.ISLAND
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = barMode,
                                    transitionSpec = {
                                        (fadeIn(tween(180, 40)) + scaleIn(initialScale = 0.85f, animationSpec = spring(0.6f, 400f)))
                                            .togetherWith(fadeOut(tween(90)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "islandContent"
                                ) { mode ->
                                    when (mode) {
                                        DynamicBarMode.ISLAND -> {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(cs.primary)
                                                )
                                                Text(
                                                    text = "Visor Island",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = cs.onSurface
                                                )
                                            }
                                        }
                                        DynamicBarMode.EXPANDED -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = cs.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Поиск по контактам...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = cs.onSurfaceVariant
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = null,
                                                    tint = cs.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        DynamicBarMode.FULL_BAR -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Заголовок экрана",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = cs.onSurface
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp,
                                                        contentDescription = null,
                                                        tint = cs.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = "Неоморфный рельеф Biolume",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cs.primary
                                                )
                                            }
                                        }
                                        DynamicBarMode.HIDDEN -> {
                                            Box(modifier = Modifier.size(8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VlButton(
                                onClick = triggerSpawnFromNothing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Из ниоткуда")
                            }

                            VlButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    barMode = when (barMode) {
                                        DynamicBarMode.ISLAND -> DynamicBarMode.EXPANDED
                                        DynamicBarMode.EXPANDED -> DynamicBarMode.FULL_BAR
                                        else -> DynamicBarMode.ISLAND
                                    }
                                }
                            ) {
                                Text("Морфинг")
                            }
                        }
                    }
                }
            }

            // ── Вкладка 1: Выливание карточек (Pouring Neumorphic Cards) ──
            if (selectedDemoTab == 1) {
                VlCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Каскадное выливание карточек",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            text = "Вязкая неоморфная перемычка между тулбаром и вытекающим блоком",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )

                        // Контейнер выливания карточек с органическим перешейком и морфингом из капли
                        val containerHeightDp = (290f + cardGapDp * 3f + 30f).dp.coerceAtLeast(340.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(containerHeightDp)
                                .vlInset(structure, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(cs.surfaceContainerLow)
                                .padding(top = 14.dp)
                                .onGloballyPositioned { coords ->
                                    containerWidthPx = coords.size.width.toFloat()
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val centerX = containerWidthPx / 2f
                            val neckW = with(density) { bridgeBaseWidthDp.dp.toPx() }
                            val maxBridgeDistPx = with(density) { (bridgeStretchGapDp * 1.5f).dp.toPx() }
                            val baseWLimitPx = with(density) { (bridgeBaseWidthDp * 0.5f).dp.toPx() }
                            val minWaistPx = with(density) { minWaistDp.dp.toPx() }
                            val toolbarH = 52f
                            val cardH = 64f
                            val toolbarY = parentRecoils[0].value
                            val toolbarBottomY = toolbarH + toolbarY
                            val cardShape = RoundedCornerShape(16.dp)

                            // ── Вычисление положений карточек (сразу в полную форму 260dp x 64dp с настраиваемым cardGapDp) ──
                            val targetTop0 = toolbarH + cardGapDp
                            val startTop0 = toolbarBottomY - 56f
                            val p0 = cardProgresses[0].value
                            val cardTopY0 = startTop0 + (targetTop0 - startTop0) * p0 + parentRecoils[1].value
                            val card0BottomY = cardTopY0 + cardH
                            val cardAlpha0 = (p0 / 0.10f).coerceIn(0f, 1f)

                            val targetTop1 = targetTop0 + cardH + cardGapDp
                            val startTop1 = card0BottomY - 56f
                            val p1 = cardProgresses[1].value
                            val cardTopY1 = startTop1 + (targetTop1 - startTop1) * p1 + parentRecoils[2].value
                            val card1BottomY = cardTopY1 + cardH
                            val cardAlpha1 = (p1 / 0.10f).coerceIn(0f, 1f)

                            val targetTop2 = targetTop1 + cardH + cardGapDp
                            val startTop2 = card1BottomY - 56f
                            val p2 = cardProgresses[2].value
                            val cardTopY2 = startTop2 + (targetTop2 - startTop2) * p2
                            val cardAlpha2 = (p2 / 0.10f).coerceIn(0f, 1f)

                            // ── ЖИДКИЕ ПЕРЕМЫЧКИ (натягиваются в зазоре между родителем и карточкой) ──
                            if (centerX > 0f && isCardsPoured) {
                                // Мостик: Тулбар -> Карточка 0
                                val gap0 = cardTopY0 - toolbarBottomY
                                val drawBridge0 = (!cardSnapped[0].value && gap0 > -2f) || cardRetractAnims[0].value > 0.001f
                                if (drawBridge0) {
                                    val barBotPx = with(density) { toolbarBottomY.dp.toPx() }
                                    val barRect = Rect(centerX - neckW / 2f, barBotPx - 8f, centerX + neckW / 2f, barBotPx)
                                    val card0TopPx = with(density) { cardTopY0.dp.toPx() }
                                    val card0Rect = Rect(centerX - (neckW * 0.92f) / 2f, card0TopPx, centerX + (neckW * 0.92f) / 2f, card0TopPx + with(density) { 16.dp.toPx() })
                                    val bridgeProg0 = (gap0 / bridgeStretchGapDp).coerceIn(0f, 1f)

                                    NeumorphicLiquidBridge(
                                        topRect = barRect,
                                        bottomRect = card0Rect,
                                        maxDistancePx = maxBridgeDistPx,
                                        isSnapped = cardSnapped[0].value,
                                        surfaceTension = surfaceTension,
                                        progress = bridgeProg0,
                                        baseWidthPx = baseWLimitPx,
                                        minWaistPx = minWaistPx,
                                        retractProgress = cardRetractAnims[0].value
                                    )
                                }

                                // Мостик: Карточка 0 -> Карточка 1
                                val gap1 = cardTopY1 - card0BottomY
                                val drawBridge1 = (!cardSnapped[1].value && gap1 > -2f) || cardRetractAnims[1].value > 0.001f
                                if (drawBridge1) {
                                    val card0BotPx = with(density) { card0BottomY.dp.toPx() }
                                    val card0BridgeRect = Rect(centerX - neckW / 2f, card0BotPx - 8f, centerX + neckW / 2f, card0BotPx)
                                    val card1TopPx = with(density) { cardTopY1.dp.toPx() }
                                    val card1Rect = Rect(centerX - (neckW * 0.92f) / 2f, card1TopPx, centerX + (neckW * 0.92f) / 2f, card1TopPx + with(density) { 16.dp.toPx() })
                                    val bridgeProg1 = (gap1 / bridgeStretchGapDp).coerceIn(0f, 1f)

                                    NeumorphicLiquidBridge(
                                        topRect = card0BridgeRect,
                                        bottomRect = card1Rect,
                                        maxDistancePx = maxBridgeDistPx,
                                        isSnapped = cardSnapped[1].value,
                                        surfaceTension = surfaceTension,
                                        progress = bridgeProg1,
                                        baseWidthPx = baseWLimitPx,
                                        minWaistPx = minWaistPx,
                                        retractProgress = cardRetractAnims[1].value
                                    )
                                }

                                // Мостик: Карточка 1 -> Карточка 2
                                val gap2 = cardTopY2 - card1BottomY
                                val drawBridge2 = (!cardSnapped[2].value && gap2 > -2f) || cardRetractAnims[2].value > 0.001f
                                if (drawBridge2) {
                                    val card1BotPx = with(density) { card1BottomY.dp.toPx() }
                                    val card1BridgeRect = Rect(centerX - neckW / 2f, card1BotPx - 8f, centerX + neckW / 2f, card1BotPx)
                                    val card2TopPx = with(density) { cardTopY2.dp.toPx() }
                                    val card2Rect = Rect(centerX - (neckW * 0.92f) / 2f, card2TopPx, centerX + (neckW * 0.92f) / 2f, card2TopPx + with(density) { 16.dp.toPx() })
                                    val bridgeProg2 = (gap2 / bridgeStretchGapDp).coerceIn(0f, 1f)

                                    NeumorphicLiquidBridge(
                                        topRect = card1BridgeRect,
                                        bottomRect = card2Rect,
                                        maxDistancePx = maxBridgeDistPx,
                                        isSnapped = cardSnapped[2].value,
                                        surfaceTension = surfaceTension,
                                        progress = bridgeProg2,
                                        baseWidthPx = baseWLimitPx,
                                        minWaistPx = minWaistPx,
                                        retractProgress = cardRetractAnims[2].value
                                    )
                                }
                            }

                            // ── КАРТОЧКА 2 (вытекает из Карточки 1) ──
                            if (isCardsPoured || cardProgresses[2].value > 0f) {
                                val p2Val = cardProgresses[2].value
                                val emerge2 = (p2Val / 0.35f).coerceIn(0f, 1f)
                                val emergeEased2 = FastOutSlowInEasing.transform(emerge2)
                                val (scaleX2, scaleY2) = calculateJellyScale(cardJellyAnims[2].value)
                                val isSnapped2 = cardSnapped[2].value

                                Box(
                                    modifier = Modifier
                                        .offset(y = cardTopY2.dp)
                                        .width(260.dp)
                                        .height(64.dp)
                                        .graphicsLayer {
                                            transformOrigin = if (!isSnapped2) TransformOrigin(0.5f, 0f) else TransformOrigin(0.5f, 1f)
                                            scaleY = (lerp(0.05f, 1f, emergeEased2) * scaleY2).coerceIn(0.05f, 2.5f)
                                            scaleX = (lerp(0.55f, 1f, emergeEased2) * scaleX2).coerceIn(0.4f, 2.5f)
                                            alpha = 1f
                                        }
                                        .vlRaised(structure, cardShape)
                                        .clip(cardShape)
                                        .background(cs.surfaceContainerHighest)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .vlInset(structure, CircleShape)
                                                .clip(CircleShape)
                                                .background(cs.surfaceContainerLow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = cs.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Карточка #3",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = cs.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Отпочковалась от Карточки #2",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // ── КАРТОЧКА 1 (вытекает из Карточки 0) ──
                            if (isCardsPoured || cardProgresses[1].value > 0f) {
                                val p1Val = cardProgresses[1].value
                                val emerge1 = (p1Val / 0.35f).coerceIn(0f, 1f)
                                val emergeEased1 = FastOutSlowInEasing.transform(emerge1)
                                val (scaleX1, scaleY1) = calculateJellyScale(cardJellyAnims[1].value)
                                val isSnapped1 = cardSnapped[1].value

                                Box(
                                    modifier = Modifier
                                        .offset(y = cardTopY1.dp)
                                        .width(260.dp)
                                        .height(64.dp)
                                        .graphicsLayer {
                                            transformOrigin = if (!isSnapped1) TransformOrigin(0.5f, 0f) else TransformOrigin(0.5f, 1f)
                                            scaleY = (lerp(0.05f, 1f, emergeEased1) * scaleY1).coerceIn(0.05f, 2.5f)
                                            scaleX = (lerp(0.55f, 1f, emergeEased1) * scaleX1).coerceIn(0.4f, 2.5f)
                                            alpha = 1f
                                        }
                                        .vlRaised(structure, cardShape)
                                        .clip(cardShape)
                                        .background(cs.surfaceContainerHighest)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .vlInset(structure, CircleShape)
                                                .clip(CircleShape)
                                                .background(cs.surfaceContainerLow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = cs.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Карточка #2",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = cs.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Отпочковалась от Карточки #1",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // ── КАРТОЧКА 0 (вытекает из Тулбара) ──
                            if (isCardsPoured || cardProgresses[0].value > 0f) {
                                val p0Val = cardProgresses[0].value
                                val emerge0 = (p0Val / 0.35f).coerceIn(0f, 1f)
                                val emergeEased0 = FastOutSlowInEasing.transform(emerge0)
                                val (scaleX0, scaleY0) = calculateJellyScale(cardJellyAnims[0].value)
                                val isSnapped0 = cardSnapped[0].value

                                Box(
                                    modifier = Modifier
                                        .offset(y = cardTopY0.dp)
                                        .width(260.dp)
                                        .height(64.dp)
                                        .graphicsLayer {
                                            transformOrigin = if (!isSnapped0) TransformOrigin(0.5f, 0f) else TransformOrigin(0.5f, 1f)
                                            scaleY = (lerp(0.05f, 1f, emergeEased0) * scaleY0).coerceIn(0.05f, 2.5f)
                                            scaleX = (lerp(0.55f, 1f, emergeEased0) * scaleX0).coerceIn(0.4f, 2.5f)
                                            alpha = 1f
                                        }
                                        .vlRaised(structure, cardShape)
                                        .clip(cardShape)
                                        .background(cs.surfaceContainerHighest)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .vlInset(structure, CircleShape)
                                                .clip(CircleShape)
                                                .background(cs.surfaceContainerLow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = cs.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Карточка #1",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = cs.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Отпочковалась от тулбара",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // ── ИСХОДНЫЙ ТУЛБАР (на самом верху стопки) ──
                            val parentShape = RoundedCornerShape(20.dp)
                            Box(
                                modifier = Modifier
                                    .offset(y = toolbarY.dp)
                                    .width(280.dp)
                                    .height(52.dp)
                                    .vlRaised(structure, parentShape)
                                    .clip(parentShape)
                                    .background(cs.surfaceContainerHighest)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(cs.primary)
                                        )
                                        Text(
                                            text = "Тулбар-источник",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = cs.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isCardsPoured) Icons.Default.KeyboardArrowUp else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VlButton(
                                onClick = {
                                    if (isCardsPoured) recallCards() else pourCards()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isCardsPoured) "Втянуть карточки" else "Вылить карточки")
                            }

                            VlButton(
                                onClick = {
                                    pourCards()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Повтор")
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // ── ПАНЕЛЬ КАЛИБРОВКИ МОСТИКА И ЖЕЛЕЙНОСТИ ──
                        VlCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Калибровка мостика и желейности",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSurface
                                    )
                                }

                                Text(
                                    text = "Быстрые пресеты физики:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Пресет 1: Экстра-желе
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .vlRaised(structure, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cs.surfaceContainerHighest)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                bridgeBaseWidthDp = 180f
                                                bridgeStretchGapDp = 24f
                                                minWaistDp = 12f
                                                jellySquashImpulse = 0.44f
                                                jellyFlightStretch = 0.22f
                                                jellyDamping = 0.22f
                                                jellyStiffness = 140f
                                                parentRecoilDp = 10f
                                                pourDurationMs = 360
                                                pourCards()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Экстра-желе",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.primary
                                        )
                                    }

                                    // Пресет 2: Густая смола
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .vlRaised(structure, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cs.surfaceContainerHighest)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                bridgeBaseWidthDp = 210f
                                                bridgeStretchGapDp = 28f
                                                minWaistDp = 18f
                                                jellySquashImpulse = 0.24f
                                                jellyFlightStretch = 0.28f
                                                jellyDamping = 0.42f
                                                jellyStiffness = 110f
                                                parentRecoilDp = 6f
                                                pourDurationMs = 460
                                                pourCards()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Густая смола",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurface
                                        )
                                    }

                                    // Пресет 3: Пружина
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .vlRaised(structure, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cs.surfaceContainerHighest)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                bridgeBaseWidthDp = 140f
                                                bridgeStretchGapDp = 15f
                                                minWaistDp = 10f
                                                jellySquashImpulse = 0.28f
                                                jellyFlightStretch = 0.12f
                                                jellyDamping = 0.35f
                                                jellyStiffness = 260f
                                                parentRecoilDp = 8f
                                                pourDurationMs = 260
                                                pourCards()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Пружина",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurface
                                        )
                                    }

                                    // Пресет 4: Сброс
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .vlRaised(structure, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cs.surfaceContainerHighest)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                bridgeBaseWidthDp = 160f
                                                bridgeStretchGapDp = 20f
                                                minWaistDp = 14f
                                                cardGapDp = 16f
                                                jellySquashImpulse = 0.32f
                                                jellyFlightStretch = 0.18f
                                                jellyDamping = 0.28f
                                                jellyStiffness = 180f
                                                parentRecoilDp = 8f
                                                pourDurationMs = 320
                                                pourCards()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Сброс",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // --- Секция 1: Параметры жидкого мостика ---
                                Text(
                                    text = "1. Жидкий мостик и натяжение",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )

                                // Слайдер: Ширина мостика
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Ширина основания мостика", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${bridgeBaseWidthDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = bridgeBaseWidthDp,
                                        onValueChange = { bridgeBaseWidthDp = it },
                                        valueRange = 60f..240f
                                    )
                                }

                                // Слайдер: Длина натяжения перешейка
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Длина натяжения перед разрывом", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${bridgeStretchGapDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = bridgeStretchGapDp,
                                        onValueChange = { bridgeStretchGapDp = it },
                                        valueRange = 8f..40f
                                    )
                                }

                                // Слайдер: Толщина струйки
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Толщина талии перед отрывом", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${minWaistDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = minWaistDp,
                                        onValueChange = { minWaistDp = it },
                                        valueRange = 4f..30f
                                    )
                                }

                                // Слайдер: Зазор между карточками
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Зазор между карточками в покое", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${cardGapDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = cardGapDp,
                                        onValueChange = { cardGapDp = it },
                                        valueRange = 8f..32f
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // --- Секция 2: Желейность и баунс ---
                                Text(
                                    text = "2. Желейность (Squash & Stretch) и динамика",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )

                                // Слайдер: Сила сплющивания
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Сила сплющивания (Squash)", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text(String.format("%.2f", jellySquashImpulse), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = jellySquashImpulse,
                                        onValueChange = { jellySquashImpulse = it },
                                        valueRange = 0.10f..0.55f
                                    )
                                }

                                // Слайдер: Растяжение в полете
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Вытягивание в падении (Stretch)", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text(String.format("%.2f", jellyFlightStretch), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = jellyFlightStretch,
                                        onValueChange = { jellyFlightStretch = it },
                                        valueRange = 0.05f..0.35f
                                    )
                                }

                                // Слайдер: Упругость/демпфирование желе
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Затухание желе (Damping Ratio)", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text(String.format("%.2f", jellyDamping), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Text("Меньше = дольше и сильнее желейная тряска", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                    Slider(
                                        value = jellyDamping,
                                        onValueChange = { jellyDamping = it },
                                        valueRange = 0.15f..0.75f
                                    )
                                }

                                // Слайдер: Жесткость пружины желе
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Жесткость пружины желе (Stiffness)", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${jellyStiffness.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = jellyStiffness,
                                        onValueChange = { jellyStiffness = it },
                                        valueRange = 60f..450f
                                    )
                                }

                                // Слайдер: Отдача родителя вверх
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Отдача тулбара вверх при отрыве", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${parentRecoilDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = parentRecoilDp,
                                        onValueChange = { parentRecoilDp = it },
                                        valueRange = 0f..18f
                                    )
                                }

                                // Слайдер: Скорость выливания
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Время шага выливания", style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                                        Text("${pourDurationMs} мс", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                                    }
                                    Slider(
                                        value = pourDurationMs.toFloat(),
                                        onValueChange = { pourDurationMs = it.toInt() },
                                        valueRange = 180f..600f
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Вкладка 2: Интерактивные метаболлы (Neumorphic Liquid Droplets) ──
            if (selectedDemoTab == 2) {
                VlCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Живые неоморфные метаболлы",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            text = "Тяните каплю пальцем: мостик натягивается, пружинит и мягко разрывается",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )

                        val maxDropDist = maxMetaballDistPx * surfaceTension

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .vlInset(structure, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(cs.surfaceContainerLow)
                                .pointerInput(surfaceTension) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dropBCenter += dragAmount
                                            val dist = (dropBCenter - dropACenter).getDistance()
                                            val isConnected = dist < maxDropDist

                                            // Упругий тактильный отклик и желейная дрожь при соединении / разрыве
                                            if (isConnected != wasConnected) {
                                                wasConnected = isConnected
                                                haptic.performHapticFeedback(
                                                    if (isConnected) HapticFeedbackType.LongPress
                                                    else HapticFeedbackType.TextHandleMove
                                                )
                                                coroutineScope.launch {
                                                    dropWobbleAnim.snapTo(1f)
                                                    dropWobbleAnim.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.35f,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val delta = dropBCenter - dropACenter
                                            val dist = delta.getDistance()
                                            coroutineScope.launch {
                                                when {
                                                    dist < maxDropDist * 0.55f -> {
                                                        // Магнитное притягивание и слияние
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        launch {
                                                            dropWobbleAnim.snapTo(1f)
                                                            dropWobbleAnim.animateTo(0f, spring(0.35f, Spring.StiffnessMediumLow))
                                                        }
                                                        val startPos = dropBCenter
                                                        val anim = Animatable(0f)
                                                        anim.animateTo(1f, spring(dampingRatio = 0.50f, stiffness = 240f)) {
                                                            dropBCenter = startPos + (dropACenter - startPos) * value
                                                        }
                                                    }
                                                    dist < maxDropDist * 1.15f -> {
                                                        // Упругий отскок после разрыва перемычки
                                                        val dir = if (dist > 0.1f) delta / dist else Offset(1f, 0f)
                                                        val target = dropACenter + dir * (maxDropDist * 1.25f)
                                                        val startPos = dropBCenter
                                                        val anim = Animatable(0f)
                                                        anim.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) {
                                                            dropBCenter = startPos + (target - startPos) * value
                                                        }
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            NeumorphicMetaballsCanvas(
                                centerA = dropACenter,
                                radiusA = dropRadiusPx,
                                centerB = dropBCenter,
                                radiusB = dropRadiusPx,
                                maxDistance = maxDropDist,
                                wobble = dropWobbleAnim.value,
                                modifier = Modifier.fillMaxSize()
                            )

                            Text(
                                text = "Свободно перетаскивайте подвижную каплю пальцем",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VlButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        // Плавное слияние с отскоком
                                        val startPos = dropBCenter
                                        val anim = Animatable(0f)
                                        launch {
                                            dropWobbleAnim.snapTo(1f)
                                            dropWobbleAnim.animateTo(0f, spring(0.38f, 300f))
                                        }
                                        anim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f)
                                        ) {
                                            dropBCenter = Offset(
                                                startPos.x + (dropACenter.x - startPos.x) * value,
                                                startPos.y + (dropACenter.y - startPos.y) * value
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text("Слить в одну")
                            }

                            VlButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        val startPos = dropBCenter
                                        val targetPos = Offset(dropCenterBX, dropCenterBY)
                                        val anim = Animatable(0f)
                                        launch {
                                            dropWobbleAnim.snapTo(1f)
                                            dropWobbleAnim.animateTo(0f, spring(0.38f, 300f))
                                        }
                                        anim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f)
                                        ) {
                                            dropBCenter = Offset(
                                                startPos.x + (targetPos.x - startPos.x) * value,
                                                startPos.y + (targetPos.y - startPos.y) * value
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text("Разделить")
                            }
                        }
                    }
                }
            }

            // ── Вкладка 3: Жидкий таббар (Liquid Segmented Control) ──
            if (selectedDemoTab == 3) {
                VlCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Жидкий сегментированный таббар",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            text = "Бегунок растягивается в направлении свайпа/клика как капля жидкости",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                        )

                        var activeSubTab by remember { mutableIntStateOf(1) }
                        NeumorphicLiquidSegmentedControl(
                            tabs = listOf("Входящие", "Отправленные", "Архив"),
                            selectedIndex = activeSubTab,
                            onTabSelected = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeSubTab = it
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        var activeColorTab by remember { mutableIntStateOf(0) }
                        NeumorphicLiquidSegmentedControl(
                            tabs = listOf("Biolume", "M3 Expressive", "Forge Industrial"),
                            selectedIndex = activeColorTab,
                            onTabSelected = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeColorTab = it
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Выбран режим: ${when(activeColorTab) { 0 -> "Biolume Рельеф"; 1 -> "Material 3E"; else -> "Forge Сталь" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = cs.primary
                        )
                    }
                }
            }

            // ── Настройки физики (доступны во всех разделах) ──
            VlCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Параметры физики и натяжения",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Поверхностное натяжение жидкости: ${String.format("%.2f", surfaceTension)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Slider(
                        value = surfaceTension,
                        onValueChange = { surfaceTension = it },
                        valueRange = 0.5f..2.2f,
                        steps = 8
                    )

                    Text(
                        text = "Упругость пружины (Bounciness): ${String.format("%.2f", bounciness)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Slider(
                        value = bounciness,
                        onValueChange = { bounciness = it },
                        valueRange = 0.35f..0.95f,
                        steps = 6
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
