package by.iposdev.visorlink.data.remote

import android.content.Context
import android.net.Uri
import by.iposdev.visorlink.data.model.bugreport.ScreenshotAttachment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Сервис загрузки скриншотов баг-репортов в защищенное хранилище Firebase Storage.
 * CDN api.visorlink.org полностью выведен из эксплуатации (Секция 1.1 спецификации).
 */
class CdnUploadService(
    private val okHttpClient: okhttp3.OkHttpClient? = null
) {

    suspend fun uploadScreenshot(file: File): ScreenshotAttachment = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Пользователь не авторизован")

        val uid = user.uid
        val fileName = "screenshot_${System.currentTimeMillis()}_${file.name}"
        val storageRef = FirebaseStorage.getInstance().reference.child("bugreports/$uid/$fileName")

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        storageRef.putFile(Uri.fromFile(file), metadata).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        ScreenshotAttachment(
            url = downloadUrl,
            cdnMediaId = "",
            fileName = file.name,
            size = file.length()
        )
    }

    suspend fun uploadScreenshotUri(context: Context, uri: Uri): ScreenshotAttachment = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Пользователь не авторизован")

        val contentResolver = context.contentResolver
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Не удалось прочитать файл скриншота")

        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val uid = user.uid
        val fileName = "screenshot_${System.currentTimeMillis()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child("bugreports/$uid/$fileName")

        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType)
            .build()

        storageRef.putBytes(bytes, metadata).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        ScreenshotAttachment(
            url = downloadUrl,
            cdnMediaId = "",
            fileName = fileName,
            size = bytes.size.toLong()
        )
    }
}
