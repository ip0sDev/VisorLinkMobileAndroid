package by.iposdev.visorlink.utils

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.UserProfile

object CustomizationHelper {
    fun shouldApplyCustomization(profileOwner: UserProfile?, currentUser: UserProfile?): Boolean {
        if (profileOwner == null || currentUser == null) return false
        if (profileOwner.uid == currentUser.uid) return true

        val isOtherPro = profileOwner.isProActive()
        val viewerIgnores = currentUser.ignoreCustomizations

        return isOtherPro && !viewerIgnores
    }

    fun parseStyle(style: String): AppTheme {
        return AppTheme.fromId(style)
    }

    fun parseAccent(accent: String): ColorPreset {
        return when (accent) {
            "purple" -> ColorPreset.PURPLE
            "blue" -> ColorPreset.BLUE
            "emerald" -> ColorPreset.EMERALD
            "crimson" -> ColorPreset.CRIMSON
            else -> ColorPreset.DEFAULT
        }
    }

    fun getTypography(fontStr: String, base: Typography): Typography {
        val fontFamily = when (fontStr) {
            "mono" -> FontFamily.Monospace
            "serif" -> FontFamily.Serif
            "rounded" -> FontFamily.SansSerif // Nunito equivalent in standard Android
            else -> return base
        }

        return Typography(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
        )
    }
}
