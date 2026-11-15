package app.planner.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val tag: String?) {
    SYSTEM(null), ARABIC("ar"), ENGLISH("en");

    companion object {
        fun current(): AppLanguage {
            val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return when {
                tags.startsWith("ar") -> ARABIC
                tags.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }

        /** Recreates the activity; layout direction follows the locale automatically (RTL for Arabic). */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
            )
        }
    }
}
