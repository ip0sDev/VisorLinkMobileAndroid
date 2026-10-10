package org.visorlink.app.ui.idcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.visorlink.app.data.idcard.ChatThemeContext

/**
 * Тема чата привязана к стеку навигации: меняется в момент навигации, а не когда экран чата
 * появился или исчез; до загрузки собеседника — прогноз по прошлому открытию.
 */
class ChatThemeControllerTest {

    private class MapMemory : ChatThemeController.Memory {
        val map = HashMap<String, ChatThemeContext>()
        override fun get(chatId: String) = map[chatId]
        override fun put(chatId: String, value: ChatThemeContext) { map[chatId] = value }
    }

    @Test
    fun `no chat in back stack means no chat theme`() {
        val c = ChatThemeController(MapMemory())
        c.setActive(null, null)
        assertNull(c.context.value)
    }

    @Test
    fun `reopened chat starts in its last known theme before the partner loads`() {
        val memory = MapMemory().apply { map["chat1"] = ChatThemeContext.NEUTRAL }
        val c = ChatThemeController(memory)
        c.setActive("entry1", "chat1")
        assertEquals("прогноз сразу, без перескока", ChatThemeContext.NEUTRAL, c.context.value)
        c.declare("entry1", "chat1", null)
        assertEquals("«ещё неизвестно» прогноз не сбрасывает", ChatThemeContext.NEUTRAL, c.context.value)
    }

    @Test
    fun `declared context wins and is remembered for the next open`() {
        val memory = MapMemory()
        val c = ChatThemeController(memory)
        c.setActive("entry1", "chat1")
        assertNull(c.context.value)
        c.declare("entry1", "chat1", ChatThemeContext.MODE)
        assertEquals(ChatThemeContext.MODE, c.context.value)
        assertEquals(ChatThemeContext.MODE, memory.map["chat1"])
    }

    @Test
    fun `leaving the chat resets the theme at once, not after the exit animation`() {
        val c = ChatThemeController(MapMemory())
        c.setActive("entry1", "chat1")
        c.declare("entry1", "chat1", ChatThemeContext.NEUTRAL)
        // Pop: запись ушла из стека — экран ещё анимируется и жив, но тема уже общая
        c.setActive(null, null)
        assertNull(c.context.value)
        // Запоздалое объявление уходящего экрана тему не возвращает
        c.declare("entry1", "chat1", ChatThemeContext.NEUTRAL)
        assertNull(c.context.value)
    }

    @Test
    fun `opening another chat on top switches to that chat`() {
        val memory = MapMemory().apply { map["chat2"] = ChatThemeContext.MODE }
        val c = ChatThemeController(memory)
        c.setActive("entry1", "chat1")
        c.declare("entry1", "chat1", ChatThemeContext.NEUTRAL)
        c.setActive("entry2", "chat2")
        assertEquals(ChatThemeContext.MODE, c.context.value)
        // Назад к первому чату — его точный контекст ещё известен
        c.setActive("entry1", "chat1")
        assertEquals(ChatThemeContext.NEUTRAL, c.context.value)
    }
}
