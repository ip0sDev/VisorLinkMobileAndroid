package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R

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
