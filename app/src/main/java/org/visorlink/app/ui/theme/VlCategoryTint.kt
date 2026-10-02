package org.visorlink.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Цвета категорий для иконок пунктов настроек.
 *
 * Это не сигнальный слой темы: оттенки одинаковы во всех темах и служат для
 * узнавания разделов («уведомления — янтарный», «хранилище — синий»). Раньше
 * SettingsScreen держал два десятка таких Color(0x…) у себя.
 */
object VlCategoryTint {
    val Amber = Color(0xFFF59E0B)
    val Pink = Color(0xFFEC4899)
    val Emerald = Color(0xFF10B981)
    val Blue = Color(0xFF3B82F6)
    val Violet = Color(0xFF8B5CF6)
    val Indigo = Color(0xFF6366F1)
    val Teal = Color(0xFF14B8A6)
    val Slate = Color(0xFF64748B)
    val Rose = Color(0xFFF43F5E)
    /** Фирменный цвет Telegram — для пунктов привязки бота. */
    val Telegram = Color(0xFF2AABEE)
    /** Золото PRO: бейджи, Биты. */
    val ProGold = Color(0xFFC5A059)
}
