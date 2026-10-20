package org.visorlink.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

/**
 * Сведения об операторе персональных данных для юридических документов (спека «ANDROID_COMPLIANCE» §8.3,
 * порт `src/utils/legalOperator.js`). В текстах документов на месте оператора стоит маркер
 * [TOKEN]; клиент обязан заменить его перед показом. Источник — `internal/legal_operator`
 * (публичное чтение, документа может не быть).
 */
object LegalOperator {
    const val TOKEN = "{{OPERATOR}}"
    const val FALLBACK_EMAIL = "privacy@visorlink.org"

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    /** Строка вместо [TOKEN], уже экранированная для HTML. [op] — поля документа или null. */
    fun format(op: Map<String, Any?>?, english: Boolean): String {
        fun pick(ru: String, en: String): String {
            val primary = (if (english) op?.get(en) else op?.get(ru))?.toString()?.trim().orEmpty()
            val secondary = (if (english) op?.get(ru) else op?.get(en))?.toString()?.trim().orEmpty()
            val value = primary.ifEmpty { secondary }
            return if (value.isEmpty()) "" else escapeHtml(value)
        }

        val name = pick("nameRu", "nameEn")
        val rawEmail = op?.get("email")?.toString().orEmpty()
        val email = escapeHtml(rawEmail.ifBlank { FALLBACK_EMAIL })
        if (name.isEmpty()) {
            return if (english) "the individual who owns the VisorLink service (contact: $email)"
            else "физическое лицо — владелец сервиса VisorLink (контакт: $email)"
        }
        val country = pick("countryRu", "countryEn")
        val address = pick("postalAddressRu", "postalAddressEn")
        val parts = if (english) {
            listOfNotNull(
                country.ifEmpty { null }?.let { "country of residence: $it" },
                "e-mail: $email",
                address.ifEmpty { null }?.let { "postal address: $it" },
            )
        } else {
            listOfNotNull(
                country.ifEmpty { null }?.let { "страна резидентства: $it" },
                "e-mail: $email",
                address.ifEmpty { null }?.let { "адрес для корреспонденции: $it" },
            )
        }
        return "${if (english) "the individual" else "физическое лицо"} $name (${parts.joinToString("; ")})"
    }

    /** Рекурсивно подставляет [operatorText] во все строки документа (`introHtml`, `content`, списки…). */
    fun apply(value: Any?, operatorText: String): Any? = when (value) {
        is String -> value.replace(TOKEN, operatorText)
        is JSONObject -> {
            val keys = value.keys().asSequence().toList()
            keys.forEach { k -> value.put(k, apply(value.opt(k), operatorText)) }
            value
        }
        is JSONArray -> {
            for (i in 0 until value.length()) value.put(i, apply(value.opt(i), operatorText))
            value
        }
        else -> value
    }

    fun apply(json: JSONObject, operatorText: String): JSONObject = apply(json as Any?, operatorText) as JSONObject
}
