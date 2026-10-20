package org.visorlink.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Значения сверены с `formatLegalOperator` из веба (`src/utils/legalOperator.js`). */
class LegalOperatorTest {

    private val op = mapOf(
        "nameRu" to "Иван Иванов", "nameEn" to "Ivan Ivanov",
        "countryRu" to "Беларусь", "countryEn" to "Belarus",
        "email" to "me@example.org",
        "postalAddressRu" to "Минск", "postalAddressEn" to "Minsk",
    )

    @Test
    fun `full operator in russian`() {
        assertEquals(
            "физическое лицо Иван Иванов (страна резидентства: Беларусь; e-mail: me@example.org; адрес для корреспонденции: Минск)",
            LegalOperator.format(op, english = false),
        )
    }

    @Test
    fun `full operator in english`() {
        assertEquals(
            "the individual Ivan Ivanov (country of residence: Belarus; e-mail: me@example.org; postal address: Minsk)",
            LegalOperator.format(op, english = true),
        )
    }

    @Test
    fun `empty parts are skipped and english falls back to russian name`() {
        val partial = mapOf("nameRu" to "Иван", "email" to "a@b.c")
        assertEquals("the individual Иван (e-mail: a@b.c)", LegalOperator.format(partial, english = true))
    }

    @Test
    fun `no document uses the owner fallback`() {
        assertEquals(
            "физическое лицо — владелец сервиса VisorLink (контакт: privacy@visorlink.org)",
            LegalOperator.format(null, english = false),
        )
        assertEquals(
            "the individual who owns the VisorLink service (contact: privacy@visorlink.org)",
            LegalOperator.format(emptyMap(), english = true),
        )
    }

    @Test
    fun `values are html-escaped`() {
        val text = LegalOperator.format(mapOf("nameRu" to "<b>A&B</b>", "email" to "x@y.z"), english = false)
        assertFalse(text.contains("<b>"))
        assertEquals("физическое лицо &lt;b&gt;A&amp;B&lt;/b&gt; (e-mail: x@y.z)", text)
    }

    @Test
    fun `token is replaced in every string field recursively`() {
        val doc = JSONObject()
            .put("introHtml", "Оператор: {{OPERATOR}}.")
            .put("sections", JSONArray().put(JSONObject().put("content", "<p>{{OPERATOR}} и {{OPERATOR}}</p>")))
            .put("keyPoints", JSONArray().put("{{OPERATOR}}").put("без маркера"))
            .put("version", "1.4.0")
        val out = LegalOperator.apply(doc, "ОП")
        assertEquals("Оператор: ОП.", out.getString("introHtml"))
        assertEquals("<p>ОП и ОП</p>", out.getJSONArray("sections").getJSONObject(0).getString("content"))
        assertEquals("ОП", out.getJSONArray("keyPoints").getString(0))
        assertEquals("без маркера", out.getJSONArray("keyPoints").getString(1))
        assertEquals("1.4.0", out.getString("version"))
    }
}
