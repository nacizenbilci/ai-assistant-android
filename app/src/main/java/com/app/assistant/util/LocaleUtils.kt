package com.app.assistant.util

import java.util.Locale

object LocaleUtils {
    fun getLocaleCode(languageCode: String): String {
        val languageCountryMapping = mapOf(
            "af" to "ZA", "sq" to "AL", "ar" to "SA", "be" to "BY", "bg" to "BG",
            "bn" to "BD", "ca" to "ES", "zh" to "CN", "hr" to "HR", "cs" to "CZ",
            "da" to "DK", "nl" to "NL", "en" to "US", "eo" to "EO", "et" to "EE",
            "fi" to "FI", "fr" to "FR", "gl" to "ES", "ka" to "GE", "de" to "DE",
            "el" to "GR", "gu" to "IN", "ht" to "HT", "he" to "IL", "hi" to "IN",
            "hu" to "HU", "is" to "IS", "id" to "ID", "ga" to "IE", "it" to "IT",
            "ja" to "JP", "kn" to "IN", "ko" to "KR", "lt" to "LT", "lv" to "LV",
            "mk" to "MK", "mr" to "IN", "ms" to "MY", "mt" to "MT", "no" to "NO",
            "fa" to "IR", "pl" to "PL", "pt" to "BR", "ro" to "RO", "ru" to "RU",
            "sk" to "SK", "sl" to "SI", "es" to "ES", "sv" to "SE", "sw" to "KE",
            "tl" to "PH", "ta" to "IN", "te" to "IN", "th" to "TH", "tr" to "TR",
            "uk" to "UA", "ur" to "PK", "vi" to "VN", "cy" to "GB"
        )
        val countryCode = languageCountryMapping[languageCode] ?: Locale.getDefault().country
        return "$languageCode-$countryCode"
    }
}
