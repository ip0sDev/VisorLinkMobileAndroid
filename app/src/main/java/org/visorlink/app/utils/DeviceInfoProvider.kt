package org.visorlink.app.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.visorlink.app.data.model.bugreport.DeviceInfo
import java.util.Locale
import java.util.TimeZone

/**
 * Провайдер системной информации устройства и версии приложения для баг-репортов.
 */
object DeviceInfoProvider {

    fun getDeviceInfo(context: Context): DeviceInfo {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val freeMemMb = memInfo.availMem / (1024 * 1024)

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            screenResolution = "${width}x${height}",
            densityDpi = density,
            language = Locale.getDefault().toLanguageTag(),
            timeZone = TimeZone.getDefault().id,
            freeMemoryMb = freeMemMb
        )
    }

    fun getAppVersion(context: Context): Pair<String, Long> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName ?: "1.0.0"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (e: PackageManager.NameNotFoundException) {
            Pair("1.0.0", 1L)
        }
    }
}
