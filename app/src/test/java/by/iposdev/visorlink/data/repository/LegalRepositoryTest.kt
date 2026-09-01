package by.iposdev.visorlink.data.repository

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.ui.graphics.Color
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.screens.legal.buildAnnotatedStringFromHtml
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileInputStream

class LegalRepositoryTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: LegalRepository

    @Before
    fun setUp() {
        var assetsDir = File("app/src/main/assets")
        if (!assetsDir.exists()) {
            assetsDir = File("src/main/assets")
        }

        assetManager = mock()
        whenever(assetManager.open("legal_tos.json")).thenAnswer {
            FileInputStream(File(assetsDir, "legal_tos.json"))
        }
        whenever(assetManager.open("legal_privacy_policy.json")).thenAnswer {
            FileInputStream(File(assetsDir, "legal_privacy_policy.json"))
        }

        context = mock()
        whenever(context.assets).thenReturn(assetManager)

        firestore = mock()
        repository = LegalRepository(firestore, context)
    }

    @Test
    fun `loadBundledVersion returns valid 1_0_0 version`() {
        val version = repository.loadBundledVersion()
        assertEquals("1.0.0", version)
    }

    @Test
    fun `loadBundledTosDocument parses all 8 sections and titles`() {
        val doc = repository.loadBundledTosDocument()
        assertNotNull(doc)
        assertEquals("terms_of_service", doc.id)
        assertEquals("Правила использования сервиса (Terms of Service)", doc.title)
        assertEquals("1.0.0", doc.version)
        assertEquals(8, doc.sections.size)

        val section1 = doc.sections.first()
        assertEquals("1", section1.number)
        assertEquals("tos-acceptance", section1.id)
        assertEquals("Предмет соглашения и порядок акцепта оферты", section1.title)
        assertEquals("Акцепт оферты", section1.badge)
        assertTrue(section1.keyPoints.isNotEmpty())
        assertTrue(section1.content.contains("1.1."))
    }

    @Test
    fun `loadBundledPrivacyDocument parses all 8 sections and privacy intro`() {
        val doc = repository.loadBundledPrivacyDocument()
        assertNotNull(doc)
        assertEquals("privacy_policy", doc.id)
        assertEquals("Политика конфиденциальности", doc.title)
        assertEquals("1.0.0", doc.version)
        assertEquals(8, doc.sections.size)

        val section2 = doc.sections[1]
        assertEquals("2", section2.number)
        assertEquals("pp-data-categories", section2.id)
        assertEquals("Состав данных", section2.badge)
        assertTrue(section2.keyPoints.isNotEmpty())
        assertTrue(section2.content.contains("2.1."))
    }

    @Test
    fun `userProfile holds acceptedAt and acceptedVersion correctly`() {
        val defaultUser = UserProfile(uid = "test_user")
        assertNull(defaultUser.acceptedAt)
        assertNull(defaultUser.acceptedVersion)

        val now = Timestamp.now()
        val agreedUser = defaultUser.copy(
            acceptedAt = now,
            acceptedVersion = "1.0.0"
        )
        assertEquals(now, agreedUser.acceptedAt)
        assertEquals("1.0.0", agreedUser.acceptedVersion)
    }

    @Test
    fun `buildAnnotatedStringFromHtml parses bold and links properly`() {
        val html = "<p class=\"legal-p\">Текст <strong>жирный</strong> и <a class=\"legal-link\" href=\"mailto:test@visorlink.org\">ссылка</a>.</p>"
        val annotated = buildAnnotatedStringFromHtml(
            html = html,
            primaryColor = Color.Cyan,
            onSurfaceColor = Color.White,
            onSurfaceVariantColor = Color.Gray
        )

        assertNotNull(annotated)
        assertTrue(annotated.text.contains("жирный"))
        assertTrue(annotated.text.contains("ссылка"))
        assertFalse(annotated.text.contains("<p"))
        assertFalse(annotated.text.contains("<strong>"))

        val urlAnnotations = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals("mailto:test@visorlink.org", urlAnnotations[0].item)
    }
}
