package by.iposdev.visorlink.utils

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class BiometricPinManagerTest {

    private val context: Context = mock()
    private val sharedPreferences: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private lateinit var biometricPinManager: BiometricPinManager

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        biometricPinManager = BiometricPinManager(context)
    }

    @Test
    fun `hasSavedPin returns true when both iv and enc are present`() {
        whenever(sharedPreferences.contains("pin_iv_user123")).thenReturn(true)
        whenever(sharedPreferences.contains("pin_enc_user123")).thenReturn(true)

        assertTrue(biometricPinManager.hasSavedPin("user123"))
    }

    @Test
    fun `hasSavedPin returns false when iv is missing`() {
        whenever(sharedPreferences.contains("pin_iv_user123")).thenReturn(false)
        whenever(sharedPreferences.contains("pin_enc_user123")).thenReturn(true)

        assertFalse(biometricPinManager.hasSavedPin("user123"))
    }

    @Test
    fun `hasSavedPin returns false when enc is missing`() {
        whenever(sharedPreferences.contains("pin_iv_user123")).thenReturn(true)
        whenever(sharedPreferences.contains("pin_enc_user123")).thenReturn(false)

        assertFalse(biometricPinManager.hasSavedPin("user123"))
    }

    @Test
    fun `getPinSecurely returns Error when no saved pin exists`() {
        whenever(sharedPreferences.getString("pin_iv_user123", null)).thenReturn(null)
        whenever(sharedPreferences.getString("pin_enc_user123", null)).thenReturn(null)

        val result = biometricPinManager.getPinSecurely("user123")
        assertTrue(result is BiometricUnlockResult.Error)
        assertEquals("Ключ биометрии не сохранен на этом устройстве.", (result as BiometricUnlockResult.Error).message)
    }

    @Test
    fun `clearSavedPin removes sharedPreferences keys`() {
        biometricPinManager.clearSavedPin("user123")

        verify(editor).remove("pin_iv_user123")
        verify(editor).remove("pin_enc_user123")
        verify(editor).apply()
    }
}
