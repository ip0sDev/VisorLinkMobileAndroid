package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R

// ── Moniqa Typeface ───────────────────────────────────────────────────────────

val MoniqaFontFamily = FontFamily(
    Font(R.font.moniqa_regular,  FontWeight.Normal),
    Font(R.font.moniqa_semibold, FontWeight.SemiBold),
    Font(R.font.moniqa_bold,     FontWeight.Bold),
)

// ── Exthru Typography ─────────────────────────────────────────────────────────

val ExthruTypography = Typography(

    // ── Display — Moniqa, крупные заголовки ───────────────────────────────────
    displayLarge = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 57.sp,
        lineHeight   = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 45.sp,
        lineHeight   = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 36.sp,
        lineHeight   = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ── Headline — Moniqa ─────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ── Title — Moniqa ────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 20.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily   = MoniqaFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 15.sp,
        lineHeight   = 22.sp,
        letterSpacing = 0.sp,
    ),

    // ── Body — системный шрифт (НЕ Moniqa) ───────────────────────────────────
    bodyLarge = TextStyle(
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // ── Label — системный шрифт (НЕ Moniqa) ──────────────────────────────────
    labelLarge = TextStyle(
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight   = FontWeight.Medium,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ── Вспомогательный стиль для имени отправителя в пузыре (Moniqa, 11sp) ──────
val ExthruSenderNameStyle = TextStyle(
    fontFamily   = MoniqaFontFamily,
    fontWeight   = FontWeight.SemiBold,
    fontSize     = 11.sp,
    lineHeight   = 16.sp,
    letterSpacing = 0.sp,
)