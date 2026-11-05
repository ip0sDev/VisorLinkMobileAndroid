package org.visorlink.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ProfileFont

/**
 * Типографика тем (паритет с Flutterlink `lib/theme/vl/vl_typography.dart`).
 *
 * Гайдлайн §5: официальная шкала M3 сохраняется целиком — кегли и интерлиньяж не
 * трогаем, меняются только гарнитуры, вес и трекинг. Поэтому базой берётся
 * дефолтная [Typography], а переопределяются лишь роли из таблицы §5.
 *
 * MATERIAL3 остаётся на системной гарнитуре: «чистый M3E» — это в том числе Roboto.
 */

// ── Гарнитуры ────────────────────────────────────────────────────────────────

/**
 * Текстовая гарнитура Biolume и Forge. Оптический размер 18pt: он рассчитан на кегли до
 * ~18sp, а вся наша шкала body/label/title лежит в 11–22sp.
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
 */
private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
)

/**
 * «Округлая» гарнитура профиля. Nunito (OFL) покрывает кириллицу целиком.
 */
private val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/**
 * Заголовки Forge: Unbounded — широкий футуристичный гротеск (OFL, кириллица целиком).
 * Все заголовочные роли в Forge рендерятся начертанием Light (w300), как в макетах приборной панели
 * (см. `vl_typography.dart` и `vl_app_bar.dart`), даже если экран передаёт `FontWeight.Bold`.
 */
private val Unbounded = FontFamily(
    Font(R.font.unbounded_light, FontWeight.Light),
    Font(R.font.unbounded_light, FontWeight.Normal),
    Font(R.font.unbounded_light, FontWeight.Medium),
    Font(R.font.unbounded_light, FontWeight.SemiBold),
    Font(R.font.unbounded_light, FontWeight.Bold),
    Font(R.font.unbounded_light, FontWeight.ExtraBold),
    Font(R.font.unbounded_light, FontWeight.Black),
)

/** Заголовочная роль Biolume: Inter SemiBold вместо Space Grotesk (кириллица). */
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
 * остались бы на системном Roboto и интерфейс мешал бы две гарнитуры.
 */
val BiolumeTypography = Typography(
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

// ── Forge: приборная типографика ─────────────────────────────────────────────

/**
 * Данные в Forge (время сообщений, счётчики в списке): Inter с табличными цифрами — моно-шрифт
 * остаётся только у LED-дисплеев, чтобы обычные подписи не читались «терминалом».
 */
val ForgeDataTypography = VlDataTypography(
    dataMedium = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    ),
    dataSmall = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    ),
)

/**
 * Forge: крупные заголовки — Unbounded Light (w300), как в макетах приборной панели; всё остальное,
 * включая кнопки — чистый Inter.
 */
val ForgeV2Typography = BiolumeTypography.copy(
    displayLarge = BiolumeTypography.displayLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light),
    displayMedium = BiolumeTypography.displayMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light),
    displaySmall = BiolumeTypography.displaySmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light),
    headlineLarge = BiolumeTypography.headlineLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
    headlineMedium = BiolumeTypography.headlineMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
    headlineSmall = BiolumeTypography.headlineSmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
    titleLarge = BiolumeTypography.titleLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
)

fun forgeV2Typography(@Suppress("UNUSED_PARAMETER") f: ForgeV2.Flavor): Typography = ForgeV2Typography

/** Заголовок раздела Forge: капитель Inter SemiBold 12 sp с трекингом 1 sp. */
val ForgeV2SectionLabel = TextStyle(
    fontFamily = TextFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 1.0.sp,
)

// ── PRO-шрифт профиля ────────────────────────────────────────────────────────

fun baseTypography(theme: AppTheme): Typography = when (theme) {
    AppTheme.BIOLUME -> BiolumeTypography
    AppTheme.FORGE_PROTOGEN, AppTheme.FORGE_BEAST -> ForgeV2Typography
    AppTheme.MATERIAL3_EXPRESSIVE -> Material3Typography
}

/**
 * Подменяет гарнитуру во всех ролях, сохраняя кегли, веса и трекинг темы.
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
