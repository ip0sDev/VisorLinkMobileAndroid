package org.visorlink.app.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.MessageType

/** Сообщение из локального кэша: поля подарка и обмена ID-картой не теряются (org.json — нужен Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ChatDataCacheMessageTest {

    private fun roundTrip(m: Message): Message = ChatDataCache.jsonToMessage(ChatDataCache.messageToJson(m))

    @Test
    fun `открытый подарок остаётся открытым`() {
        val back = roundTrip(
            Message(id = "g", senderId = "system", type = MessageType.GIFT, redeemed = true, redeemedByUid = "u2", redeemedByUsername = "foxy", giftType = "pro"),
        )
        assertTrue(back.redeemed)
        assertEquals("u2", back.redeemedByUid)
        assertEquals("foxy", back.redeemedByUsername)
        assertEquals("pro", back.giftType)
        assertFalse(back.unknownPayload.containsKey("redeemed"))
    }

    @Test
    fun `обмен ID-картой сохраняет tradeId`() {
        assertEquals("T1", roundTrip(Message(id = "t", senderId = "u", type = MessageType.ID_TRADE, tradeId = "T1")).tradeId)
        assertNull(roundTrip(Message(id = "x", senderId = "u")).tradeId)
        assertFalse(roundTrip(Message(id = "x", senderId = "u")).redeemed)
    }
}
