package by.iposdev.visorlink.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextParserTest {

    @Test
    fun testStripMarkdownRemovesAllTags() {
        val input = "🛠️ **Ваш баг-репорт принят в работу!** Тикет: *«Название»* (#14) `commit-hash` ~~old~~"
        val expected = "🛠️ Ваш баг-репорт принят в работу! Тикет: «Название» (#14) commit-hash old"
        val actual = MarkdownTextParser.stripMarkdown(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testStripMarkdownWithEmptyOrNull() {
        assertEquals("", MarkdownTextParser.stripMarkdown(null))
        assertEquals("", MarkdownTextParser.stripMarkdown(""))
        assertEquals("", MarkdownTextParser.stripMarkdown("   "))
    }

    @Test
    fun testParseMarkdownStylesContent() {
        val input = "**Bold** and *Italic* and `Code` and ~~Strike~~"
        val annotated = MarkdownTextParser.parse(input)
        assertEquals("Bold and Italic and Code and Strike", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
