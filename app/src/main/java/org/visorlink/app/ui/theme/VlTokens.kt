package org.visorlink.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Расширение M3-токенов, которое M3 не покрывает: неоморфный рельеф, сигнальное
 * свечение, success/warning и моноширинные data-роли.
 *
 * Компоненты читают это через [LocalVlTokens] и НЕ принимают тему параметром —
 * поэтому экраны остаются полностью тема-независимыми: они вызывают `VlSurface`,
 * `VlButton` и т.д. с той же подписью для любой темы.
 *
 * Биолюм-гайдлайн §4: глубина разделена на два независимых слоя —
 *  - **структура** ([VlStructureTokens]) — нейтральный рельеф, всегда;
 *  - **сигнал** ([VlSignalTokens]) — цветное свечение, только focus/press/live.
 */

// ── Стиль оформления ─────────────────────────────────────────────────────────

enum class VlStyle {
    /** Чистый Material 3 Expressive: плоские поверхности, elevation средствами M3. */
    MATERIAL3,

    /** Biolume: неоморфный рельеф + редкий сигнальный неон. */
    BIOLUME,

    /**
     * Forge v2: терминал особых режимов ID-карты (Protogen / Beast). Только тёмная,
     * контур вместо теней, малые скругления — см. ForgeV2Palette.kt.
     */
    FORGE_V2,
}

/**
 * Роль элемента в слое структуры (гайдлайн §4.1).
 *
 * Логика не декоративная: элементы, которые *на что-то нажимают* — [Raised],
 * элементы, которые *во что-то принимают* — [Inset].
 */
enum class VlDepth {
    /** Карточки, tonal/outlined-кнопки, чипы в покое, thumb тумблера. */
    Raised,

    /** Текстовые поля, трек тумблера, нажатая кнопка, выбранный чип. */
    Inset,

    /** Без рельефа (заливка сама несёт смысл: filled-кнопка, аватар). */
    Flat,
}

// ── Слой структуры ───────────────────────────────────────────────────────────

/**
 * Мягкий двойной свет/тень от одного невидимого источника сверху-слева.
 * Значения — из таблицы гайдлайна §4.1; alpha уже вложена в цвета.
 */
@Immutable
data class VlStructureTokens(
    /** false для MATERIAL3 — все структурные модификаторы становятся no-op. */
    val enabled: Boolean,
    val shadowDark: Color,
    val shadowLight: Color,
    val raisedOffset: Dp = 6.dp,
    val raisedBlur: Dp = 14.dp,
    val raisedLightOffset: Dp = 5.dp,
    val raisedLightBlur: Dp = 12.dp,
    val insetOffset: Dp = 4.dp,
    val insetBlur: Dp = 8.dp,
    val insetLightOffset: Dp = 3.dp,
    val insetLightBlur: Dp = 6.dp,
    /**
     * Не null — плоская панель с контуром этого цвета вместо неоморфного рельефа
     * (Forge v2): raised = контур + короткая мягкая тень вниз, inset = контур +
     * внутренняя тень сверху.
     */
    val outline: Color? = null,
) {
    companion object {
        /** M3E рельефом не пользуется — elevation остаётся за Material. */
        val Disabled = VlStructureTokens(
            enabled = false,
            shadowDark = Color.Transparent,
            shadowLight = Color.Transparent,
        )
    }
}

// ── Слой сигнала ─────────────────────────────────────────────────────────────

/**
 * Цветное свечение поверх структуры. Гайдлайн §4.2 + §10: в покое НЕ светится
 * ничего, кроме FAB, и на экране одновременно активен максимум один glow.
 */
@Immutable
data class VlSignalTokens(
    val enabled: Boolean,
    /** Радиус размытия glow (§3: 12px в Abyss, 10px в Tidepool). */
    val glowBlur: Dp = 12.dp,
    /** Непрозрачность glow (§3: .28 в Abyss, .18 в Tidepool). */
    val glowAlpha: Float = 0.28f,
    /** Постоянное (но статичное) свечение FAB — единственное исключение из §10. */
    val fabRestAlpha: Float = 0.16f,
    /** Толщина сигнального контура у сфокусированного поля. */
    val focusBorder: Dp = 1.dp,
    /** Период «биопульса» в мс (§6). */
    val pulsePeriodMs: Int = 2400,
    /**
     * Постоянное свечение основной кнопки (Forge v2: неон горит и в покое).
     * 0 — кнопка светится только при нажатии, как требует §10 Biolume.
     */
    val buttonRestAlpha: Float = 0f,
) {
    companion object {
        val Disabled = VlSignalTokens(enabled = false, glowAlpha = 0f, fabRestAlpha = 0f)
    }
}

// ── Формы ────────────────────────────────────────────────────────────────────

/**
 * Формы берутся из M3/M3E буквально (гайдлайн §2), Biolume меняет только то,
 * что спецификация допускает: stadium-кнопки и асимметричный FAB из shape-набора
 * M3 Expressive. Forge заменяет всё на прямые углы.
 *
 * [bar], [pill], [indicator] и [avatar] существуют потому, что раньше эти формы
 * были зашиты в компонентах константами (`CircleShape`,
 * `RoundedCornerShape(percent = 50)`) — и тема с прямыми углами не могла их
 * переопределить.
 */
@Immutable
data class VlShapeTokens(
    val button: Shape,
    /** Морфинг формы при нажатии — паттерн M3E (§2, §7). */
    val buttonPressed: Shape,
    val card: Shape,
    val field: Shape,
    val chip: Shape,
    val fab: Shape,
    /** Контейнер нижней навигации. */
    val bar: Shape,
    /** Панель ввода чата. Фиксированный радиус: высота панели растёт при реплаях,
     *  и процентная форма (как у [bar]) ломала бы скругления. */
    val inputPanel: Shape,
    /** Stadium-подобное: pill навигации, трек тумблера. */
    val pill: Shape,
    /** Мелкие круглые элементы: точки статуса, бегунок тумблера, кружки акцентов. */
    val indicator: Shape,
    /** Аватары. Квадратные аватары — сильный индустриальный сигнал. */
    val avatar: Shape,
    /** Радиус, от которого считается рельеф и сигнальный контур. */
    val cardRadius: Dp,
    val buttonRadius: Dp,
    /**
     * Контейнер группы настроек ([VlSettingsSection]). Отдельно от [card]: у
     * Biolume карточка 16dp, а секция исторически 24dp — раньше это было
     * зашито в компонент, и Forge получал скругления вопреки своей шкале.
     */
    val section: Shape = RoundedCornerShape(24.dp),
    /** Строка выбора ([VlOptionRow]) — подсветка выбранного варианта. */
    val row: Shape = RoundedCornerShape(12.dp),
    /**
     * Потолок любого скругления (Forge v2: 8–10 dp). Произвольные формы экранов
     * ([adapt], [rounded]) зажимаются до него — иначе «жидкие» 32 dp в терминале.
     */
    val maxRadius: Dp? = null,
) {
    /** Шкала без скруглений. */
    val isSquare: Boolean get() = cardRadius == 0.dp

    /**
     * Любая форма, кроме формы шкалы без скруглений: там — прямой угол.
     *
     * Компоненты и экраны рисуют много своих форм (`RoundedCornerShape(12.dp)`,
     * `CircleShape`), и раньше Forge получал их как есть — «ни одного
     * скругления» нарушалось в каждом листе, плеере и диалоге. Все такие формы
     * в `ui/components` и `ui/screens` проходят через [adapt]; для скруглённых
     * тем он возвращает форму без изменений.
     */
    fun adapt(shape: Shape): Shape = when {
        isSquare -> RectangleShape
        maxRadius != null && shape is CornerBasedShape -> ClampedCornerShape(shape, maxRadius)
        else -> shape
    }

    /**
     * Скругление на [radius]. В частности — для «жидких» компонентов, которые
     * скругляют сильнее обычной шкалы.
     */
    fun rounded(radius: Dp): Shape = adapt(RoundedCornerShape(radius))
}

/** Скругления формы, зажатые до [max] (проценты пересчитываются от реального размера). */
private class ClampedCornerShape(private val base: CornerBasedShape, private val max: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val limit = with(density) { max.toPx() }
        fun clamp(c: CornerSize) = CornerSize(c.toPx(size, density).coerceAtMost(limit))
        return base.copy(clamp(base.topStart), clamp(base.topEnd), clamp(base.bottomEnd), clamp(base.bottomStart))
            .createOutline(size, layoutDirection, density)
    }

    override fun equals(other: Any?) = other is ClampedCornerShape && other.base == base && other.max == max
    override fun hashCode() = base.hashCode() * 31 + max.hashCode()
}

// ── Движение ─────────────────────────────────────────────────────────────────

/** Как элемент реагирует на нажатие. Физика у тем принципиально разная. */
enum class VlPressStyle {
    /** M3E: лёгкое сжатие. */
    SCALE,

    /** Biolume: raised → inset, рельеф сам сообщает нажатие (гайдлайн §4.1). */
    INSET,
}

/**
 * Тайминги и характер анимаций.
 *
 * Спеки не хранятся готовыми объектами, а собираются из примитивов функцией
 * [motionSpec]: `AnimationSpec` не помечен `@Immutable`, и держать его в
 * `@Immutable`-классе значило бы соврать компилятору о стабильности.
 */
@Immutable
data class VlMotionTokens(
    val pressStyle: VlPressStyle,
    /** Множитель сжатия для [VlPressStyle.SCALE]. */
    val pressScale: Float,
    val useSpring: Boolean,
    val dampingRatio: Float,
    val stiffness: Float,
    /** Длительность для линейных переходов (когда [useSpring] == false). */
    val durationMs: Int,
    /**
     * Терминальный характер движения (Forge v2): вместо жидких эффектов — ступенчатые
     * переходы без пружин и перелётов, «глитч»-дрожание вместо желе, раскрытие заголовков
     * курсором. См. `ui/components/TerminalMotion.kt`.
     */
    val glitch: Boolean = false,
)

// ── Статусные цвета (нет в M3 ColorScheme) ───────────────────────────────────

/**
 * Плотная заливка выбранного элемента: pill в навигации, выбранный чип, активный
 * сегмент.
 *
 * Отдельная роль нужна потому, что `primaryContainer` в Biolume — это primary с
 * alpha .12 (§3), и на поверхности контейнера он почти не читается: выделение
 * выглядит как отсутствие выделения. Здесь тон подмешан плотно, но заметно слабее
 * filled-CTA, чтобы выбор не читался как кнопка (§4.2: выбор сигналится цветом и
 * формой, без свечения).
 */
@Immutable
data class VlStatusTokens(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
)

// ── Моноширинные data-роли (гайдлайн §5, §1.5) ───────────────────────────────

/** Числа, таймштампы, ID — визуально отделены от «авторского» текста. */
@Immutable
data class VlDataTypography(
    val dataMedium: TextStyle,
    val dataSmall: TextStyle,
)

// ── Бабблы чата ──────────────────────────────────────────────────────────────

/**
 * Цвета бабблов — единственная роль, которой нет ни в M3, ни в гайдлайне.
 *
 * В M3E повторяет прежнее поведение (`primaryContainer` / `surfaceVariant`).
 * В Biolume `primaryContainer` — заливка с alpha .12, для баббла она слишком
 * бледная, поэтому «мой» баббл берёт плотный подмешанный тон.
 *
 * Рельефа у бабблов сознательно НЕТ: `vlRaised` — это два прохода
 * `setShadowLayer` на элемент, а бабблы это плотный переиспользуемый контент в
 * прокручиваемом списке. Структуру им задаёт заливка и нейтральная грань.
 */
@Immutable
data class VlBubbleTokens(
    val mineBg: Color,
    val mineFg: Color,
    val otherBg: Color,
    val otherFg: Color,
)

// ── Корневой контракт ────────────────────────────────────────────────────────

// ── Терминальная типографика (Forge v2) ──────────────────────────────────────

/**
 * Терминал Forge v2: заголовки разделов — моноширинная капитель в цвете primary с линией до
 * края (у Protogen — префикс `> `), неоновая линия под верхней панелью и фон поверх
 * интерфейса ([vlTerminalBackdrop]): дымка и сканлайны. `null` в остальных темах.
 */
@Immutable
data class VlTerminalTokens(
    val labelPrefix: String,
    /** Сканлайны: белый с этой непрозрачностью каждые 3 px, 0 — без них. */
    val scanlineAlpha: Float = 0f,
    /** Неоновые линии: основной цвет и второй (градиент вдоль линии). */
    val neon: Color = Color.Unspecified,
    val neonAlt: Color = Color.Unspecified,
    /** Неоновая дымка сверху экрана, `null` — без неё. */
    val haze: Color? = null,
) {
    val scanlines: Boolean get() = scanlineAlpha > 0f
}

/**
 * Тумблер ([org.visorlink.app.ui.components.VlSwitch]): формы и размер бегунка. По умолчанию —
 * трек [VlShapeTokens.pill] и круглый бегунок 24 dp, как было всегда; Forge v2 вписывает
 * бегунок в свой трек со скруглением 6–8 dp.
 */
@Immutable
data class VlSwitchTokens(
    /** `null` — [VlShapeTokens.pill]. */
    val track: Shape? = null,
    /** `null` — [VlShapeTokens.indicator]. */
    val thumb: Shape? = null,
    val thumbWidth: Dp = 24.dp,
    val thumbHeight: Dp = 24.dp,
    /** Неон бегунка во включённом состоянии (непрозрачность свечения), 0 — без него. */
    val thumbGlow: Float = 0f,
    /** Во включённом состоянии трек подсвечен: контур `primary` и более яркая заливка. */
    val litTrack: Boolean = false,
)

@Immutable
data class VlTokens(
    val style: VlStyle,
    val isDark: Boolean,
    val structure: VlStructureTokens,
    val signal: VlSignalTokens,
    val shapes: VlShapeTokens,
    val motion: VlMotionTokens,
    val status: VlStatusTokens,
    /** Заливка выбранного pill/чипа/сегмента — см. комментарий у [VlStatusTokens]. */
    val selectionFill: Color,
    val bubbles: VlBubbleTokens,
    val data: VlDataTypography,
    /**
     * Системная настройка «убрать анимации». Читается один раз в [VisorLinkTheme]
     * и раздаётся вниз, чтобы каждый компонент не дёргал Settings сам.
     * Гайдлайн §6/§8: биопульс заменяется статичным glow той же интенсивности.
     */
    val reduceMotion: Boolean,
    val terminal: VlTerminalTokens? = null,
    val switch: VlSwitchTokens = VlSwitchTokens(),
) {
    val isBiolume: Boolean get() = style == VlStyle.BIOLUME

    /** Терминальная анимация вместо жидкой (Forge v2), если система не просит убрать анимации. */
    val glitchMotion: Boolean get() = motion.glitch && !reduceMotion
}

/**
 * Анимационный спек темы. Пружина для M3E/Biolume, линейный переход для Forge —
 * механика не пружинит.
 */
fun <T> VlMotionTokens.motionSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (useSpring) {
        androidx.compose.animation.core.spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
        )
    } else {
        androidx.compose.animation.core.tween(
            durationMillis = durationMs,
            easing = androidx.compose.animation.core.LinearEasing,
        )
    }

private val FallbackShapes = VlShapeTokens(
    button = RoundedCornerShape(16.dp),
    buttonPressed = RoundedCornerShape(12.dp),
    card = RoundedCornerShape(24.dp),
    field = RoundedCornerShape(16.dp),
    chip = RoundedCornerShape(50),
    fab = RoundedCornerShape(16.dp),
    bar = RoundedCornerShape(32.dp),
    inputPanel = RoundedCornerShape(28.dp),
    pill = RoundedCornerShape(percent = 50),
    indicator = CircleShape,
    avatar = CircleShape,
    cardRadius = 24.dp,
    buttonRadius = 16.dp,
)

/**
 * Фолбэк нужен только для @Preview и вызовов вне [VisorLinkTheme]; в рантайме
 * значение всегда переопределяется темой.
 */
val LocalVlTokens = staticCompositionLocalOf {
    VlTokens(
        style = VlStyle.MATERIAL3,
        isDark = false,
        structure = VlStructureTokens.Disabled,
        signal = VlSignalTokens.Disabled,
        shapes = FallbackShapes,
        motion = VlMotionTokens(
            pressStyle = VlPressStyle.SCALE,
            pressScale = 0.97f,
            useSpring = true,
            dampingRatio = 1f,
            stiffness = 1500f,
            durationMs = 200,
        ),
        status = VlStatusTokens(
            success = Color(0xFF4C9A2A),
            onSuccess = Color.White,
            warning = Color(0xFFB8790A),
            onWarning = Color.White,
        ),
        selectionFill = Color.Unspecified,
        bubbles = VlBubbleTokens(
            mineBg = Color.Unspecified,
            mineFg = Color.Unspecified,
            otherBg = Color.Unspecified,
            otherFg = Color.Unspecified,
        ),
        data = VlDataTypography(
            dataMedium = TextStyle.Default,
            dataSmall = TextStyle.Default,
        ),
        reduceMotion = false,
    )
}

/** Точка доступа: `VlTheme.tokens.structure`, по аналогии с `MaterialTheme.colorScheme`. */
object VlTheme {
    val tokens: VlTokens
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalVlTokens.current
}
