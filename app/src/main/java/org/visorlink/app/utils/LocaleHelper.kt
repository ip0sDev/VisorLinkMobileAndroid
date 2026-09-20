package org.visorlink.app.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    EN("en"),
    RU("ru");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: SYSTEM

        fun systemDefault(): AppLanguage {
            val systemLang = Locale.getDefault().language
            return if (systemLang == "ru") RU else EN
        }
    }
}

object LocaleHelper {

    fun applyLanguage(language: AppLanguage) {
        val localeList = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.EN     -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.RU     -> LocaleListCompat.forLanguageTags("ru")
        }
        // Устанавливает язык и АВТОМАТИЧЕСКИ пересоздает Activity.
        // Compose сам перерисует все stringResource() на лету!
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    // НОВЫЙ МЕТОД: Получает текущий сохраненный язык
    fun getCurrentLanguage(): AppLanguage {
        val localeList = AppCompatDelegate.getApplicationLocales()
        return if (localeList.isEmpty) {
            AppLanguage.SYSTEM
        } else {
            val lang = localeList.get(0)?.language ?: return AppLanguage.SYSTEM
            AppLanguage.fromCode(lang)
        }
    }
}