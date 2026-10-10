package org.visorlink.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ProfileFont

/**
 * Типографика тем.
 *
 * Гайдлайн §5: официальная шкала M3 сохраняется целиком — кегли и интерлиньяж не
 * трогаем, меняются только гарнитуры, вес и трекинг. Поэтому базой берётся
 * дефолтная [Typography], а переопределяются лишь роли из таблицы §5.
 *
 * MATERIAL3 остаётся на системной гарнитуре: «чистый M3E» — это в том числе Roboto.
 */

// ── Гарнитуры ────────────────────────────────────────────────────────────────

/**
 * Текстовая гарнитура Biolume. Оптический размер 18pt: он рассчитан на кегли до
 * ~18sp, а вся наша шкала body/label/title лежит в 11–22sp (24/28pt-версии Inter
 * предназначены для display и на мелком тексте выглядят разреженно).
 */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Data-роли §5: числа, таймштампы, ID. Кириллицу покрывает полностью. */
private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/**
 * Space Grotesk — ТОЛЬКО для латинского вордмарка, см. [BiolumeBrandStyle].
 *
 * Гайдлайн §5 отдаёт ему display / headline / titleLarge, но в шрифте **нет ни
 * одного кириллического глифа** (проверено по таблице cmap: отсутствуют все 66
 * букв). В русскоязычном интерфейсе это дало бы заголовки системным шрифтом, а в
 * смешанных строках — две гарнитуры в одном слове. Поэтому заголовочные роли
 * отданы Inter SemiBold, а Space Grotesk остался там, где текст латинский по
 * определению — на названии приложения.
 */
private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
)

/**
 * «Округлая» гарнитура профиля. Раньше `rounded` отображался в
 * `FontFamily.SansSerif` — это тот же Roboto, и выбор в редакторе ничего не менял.
 * Nunito (OFL, лицензия в `licenses/nunito-OFL.txt`) покрывает кириллицу целиком;
 * веса — статические инстансы вариативного шрифта.
 */
private val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/**
 * Заголовки Forge v2: Unbounded — широкий футуристичный гротеск (OFL, кириллица целиком).
 * Medium — основное начертание; SemiBold нужен жирным заголовкам (диалог), чтобы Android
 * не утолщал Medium синтетически.
 */
private val Unbounded = FontFamily(
    Font(R.font.unbounded_medium, FontWeight.Medium),
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
)

/** Заголовочная роль: Inter SemiBold вместо Space Grotesk — причина выше. */
private val DisplayFamily = Inter

/** Текстовая: title / body / label. */
private val TextFamily = Inter

/** Данные: числа, таймштампы, ID. */
private val DataFamily = JetBrainsMono

/**
 * Вордмарк «VisorLink». Единственное место, где живёт Space Grotesk, — латиница
 * здесь гарантирована в обеих локалях (`app_name` не переводится).
 */
val BiolumeBrandStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.SemiBold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
)

// ── M3 Expressive: чистая дефолтная шкала ────────────────────────────────────

/** Ничего не переопределяем — это и есть «чистый M3E» из требований. */
val Material3Typography = Typography()

val Material3DataTypography = VlDataTypography(
    dataMedium = TextStyle(
        fontFamily = DataFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    dataSmall = TextStyle(
        fontFamily = DataFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

// ── Biolume: парная типографика (§5) ─────────────────────────────────────────

private val BiolumeBase = Typography()

/**
 * Веса из таблицы §5 применены к названным там ролям; ОСТАЛЬНЫЕ роли тоже
 * переводятся на [TextFamily] — иначе `bodySmall`, `titleSmall` и `labelMedium`
 * остались бы на системном Roboto и интерфейс мешал бы две гарнитуры. Кегли и
 * интерлиньяж везде оставлены дефолтные M3 (§5: «шкала не меняется»).
 */
val BiolumeTypography = Typography(
    // §5: Space Grotesk-роли → Inter SemiBold/Medium (кириллица, см. выше).
    displayLarge = BiolumeBase.displayLarge.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
    ),
    displayMedium = BiolumeBase.displayMedium.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp,
    ),
    displaySmall = BiolumeBase.displaySmall.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
    ),
    headlineLarge = BiolumeBase.headlineLarge.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp,
    ),
    headlineMedium = BiolumeBase.headlineMedium.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = BiolumeBase.headlineSmall.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = BiolumeBase.titleLarge.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Medium,
    ),

    // §5: Inter-роли.
    titleMedium = BiolumeBase.titleMedium.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
    ),
    titleSmall = BiolumeBase.titleSmall.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = BiolumeBase.bodyLarge.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal,
    ),
    bodyMedium = BiolumeBase.bodyMedium.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal,
    ),
    bodySmall = BiolumeBase.bodySmall.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal,
    ),
    labelLarge = BiolumeBase.labelLarge.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
    ),
    labelMedium = BiolumeBase.labelMedium.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = BiolumeBase.labelSmall.copy(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
    ),
)

/** Доп. роли §5 — в M3 Typography их нет, поэтому живут в [VlTokens]. */
val BiolumeDataTypography = VlDataTypography(
    dataMedium = TextStyle(
        fontFamily = DataFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    dataSmall = TextStyle(
        fontFamily = DataFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

// ── Forge v2: терминал ───────────────────────────────────────────────────────

/** Моно-данные Forge v2: JetBrains Mono с лёгким трекингом. */
val ForgeDataTypography = VlDataTypography(
    dataMedium = TextStyle(
        fontFamily = DataFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    dataSmall = TextStyle(
        fontFamily = DataFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Forge v2: крупные заголовки (display, headline, titleLarge — в том числе заголовок верхней
 * панели) — Unbounded; метки и текст кнопок (labelLarge) — моно с трекингом, как командная
 * строка; текст сообщений и описаний — Inter, как в вебе (--font-ui). Капитель у заголовков
 * разделов задаёт [VlTerminalTokens], а не шкала. В вебе заголовки — JetBrains Mono
 * (--font-brand); Unbounded — Android-слой, см. спеку §7.
 */
val ForgeV2Typography = BiolumeTypography.copy(
    displayLarge = BiolumeTypography.displayLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium),
    displayMedium = BiolumeTypography.displayMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium),
    displaySmall = BiolumeTypography.displaySmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium),
    headlineLarge = BiolumeTypography.headlineLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    headlineMedium = BiolumeTypography.headlineMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    headlineSmall = BiolumeTypography.headlineSmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    titleLarge = BiolumeTypography.titleLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelLarge = BiolumeTypography.labelLarge.copy(fontFamily = DataFamily, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

/**
 * Шкала оттенка: крупные заголовки (display, headline, titleLarge — в том числе заголовок
 * верхней панели) светятся неоном своего основного цвета, у Beast мягче.
 */
fun forgeV2Typography(f: ForgeV2.Flavor): Typography {
    val glow = Shadow(color = f.primary.copy(alpha = 0.4f * f.neon), offset = Offset.Zero, blurRadius = 14f * f.neon)
    fun TextStyle.neon() = copy(shadow = glow)
    return with(ForgeV2Typography) {
        copy(
            displayLarge = displayLarge.neon(), displayMedium = displayMedium.neon(), displaySmall = displaySmall.neon(),
            headlineLarge = headlineLarge.neon(), headlineMedium = headlineMedium.neon(), headlineSmall = headlineSmall.neon(),
            titleLarge = titleLarge.neon(),
        )
    }
}

private val ForgeV2ProtogenTypography = forgeV2Typography(ForgeV2.Protogen)
private val ForgeV2BeastTypography = forgeV2Typography(ForgeV2.Beast)

/** Заголовок раздела Forge v2: моно, 11 sp, трекинг 0.08em. */
val ForgeV2SectionLabel = TextStyle(
    fontFamily = DataFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 0.9.sp,
)

// ── PRO-шрифт профиля ────────────────────────────────────────────────────────

fun baseTypography(theme: AppTheme): Typography = when (theme) {
    AppTheme.BIOLUME -> BiolumeTypography
    AppTheme.FORGE_PROTOGEN -> ForgeV2ProtogenTypography
    AppTheme.FORGE_BEAST -> ForgeV2BeastTypography
    AppTheme.MATERIAL3_EXPRESSIVE -> Material3Typography
}

/**
 * Подменяет гарнитуру во всех ролях, сохраняя кегли, веса и трекинг темы.
 * Моно — JetBrains Mono, а не системный моноширинный: тот без кириллицы на
 * части прошивок и не совпадает с data-ролями.
 */
fun Typography.withProfileFont(font: ProfileFont): Typography {
    val family = when (font) {
        ProfileFont.MONO -> JetBrainsMono
        ProfileFont.SERIF -> FontFamily.Serif
        ProfileFont.ROUNDED -> Nunito
    }
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
