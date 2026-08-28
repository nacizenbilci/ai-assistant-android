package com.app.assistant.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.app.assistant.BuildConfig
import com.app.assistant.viewmodel.MainViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdater {

    private const val TAG = "SabanUpdater"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/nacizenbilci/ai-assistant-android/releases/latest"

    private const val PREFS = "saban_updater"
    private const val PENDING_APK = "pending_apk"

    private val checking = AtomicBoolean(false)

    fun checkForUpdates(activity: Activity) {

        if (BuildConfig.DEBUG) return

        if (!checking.compareAndSet(false, true)) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val client = MainViewModelFactory.okHttpClient

                val request = Request.Builder()
                    .url(LATEST_RELEASE_URL)
                    .header("User-Agent", "Saban-Android-Updater")
                    .build()

                val jsonString = client
                    .newCall(request)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            throw IOException(
                                "Release check HTTP ${response.code}"
                            )
                        }

                        response.body?.string()
                            ?: throw IOException(
                                "Empty GitHub release response"
                            )
                    }

                val json = JSONObject(jsonString)

                val tag = json.optString("tag_name")

                val latestBuild =
                    Regex("""saban-build-(\d+)""")
                        .find(tag)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: return@launch

                // Gradle:
                // versionCode = 2 + GITHUB_RUN_NUMBER
                val currentBuild =
                    (BuildConfig.VERSION_CODE - 2)
                        .coerceAtLeast(0)

                Log.i(
                    TAG,
                    "Current=$currentBuild Latest=$latestBuild"
                )

                if (latestBuild <= currentBuild) {
                    return@launch
                }

                val assets =
                    json.optJSONArray("assets")
                        ?: return@launch

                var apkUrl: String? = null

                for (i in 0 until assets.length()) {

                    val asset =
                        assets.getJSONObject(i)

                    if (
                        asset.optString("name") ==
                        "Saban-SM-P587.apk"
                    ) {

                        apkUrl =
                            asset.optString(
                                "browser_download_url"
                            )

                        break
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    throw IOException(
                        "Saban APK release asset bulunamadı"
                    )
                }

                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Yeni ŞABAN sürümü indiriliyor...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val updateDir =
                    File(
                        activity.cacheDir,
                        "updates"
                    ).apply {
                        mkdirs()
                    }

                val apkFile =
                    File(
                        updateDir,
                        "Saban-update-$latestBuild.apk"
                    )

                val apkRequest =
                    Request.Builder()
                        .url(apkUrl)
                        .header(
                            "User-Agent",
                            "Saban-Android-Updater"
                        )
                        .build()

                client
                    .newCall(apkRequest)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            throw IOException(
                                "APK download HTTP ${response.code}"
                            )
                        }

                        val body =
                            response.body
                                ?: throw IOException(
                                    "APK body empty"
                                )

                        body.byteStream().use { input ->

                            apkFile
                                .outputStream()
                                .use { output ->

                                    input.copyTo(output)
                                }
                        }
                    }

                activity
                    .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putString(
                        PENDING_APK,
                        apkFile.absolutePath
                    )
                    .apply()

                activity.runOnUiThread {
                    installPendingUpdate(activity)
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Update check failed",
                    e
                )

            } finally {

                checking.set(false)
            }
        }
    }

    fun resumePendingInstall(
        activity: Activity
    ) {

        if (BuildConfig.DEBUG) return

        installPendingUpdate(activity)
    }

    private fun installPendingUpdate(
        activity: Activity
    ) {

        val prefs =
            activity.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val path =
            prefs.getString(
                PENDING_APK,
                null
            ) ?: return

        val apkFile =
            File(path)

        if (!apkFile.exists()) {

            prefs.edit()
                .remove(PENDING_APK)
                .apply()

            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
            !activity.packageManager
                .canRequestPackageInstalls()
        ) {

            Toast.makeText(
                activity,
                "Bir kez 'Bu kaynaktan izin ver' seçeneğini aç.",
                Toast.LENGTH_LONG
            ).show()

            try {

                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse(
                            "package:${activity.packageName}"
                        )
                    )

                activity.startActivity(intent)

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Install permission settings açılamadı",
                    e
                )
            }

            return
        }

        val apkUri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

        val installIntent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive"
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        prefs.edit()
            .remove(PENDING_APK)
            .apply()

        activity.startActivity(
            installIntent
        )
    }
}
