package com.musicpipe.player.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.data.remote.ApiException
import com.musicpipe.player.data.remote.MusicPipeApi
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.remote.dto.LatestReleaseDto
import com.musicpipe.player.data.remote.unwrap
import com.musicpipe.player.util.Md5
import com.musicpipe.player.util.VersionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * In-app update per docs/路由器整合报告.md §4.3 + §6.
 * GET /api/v1/app/update/latest (Bearer) -> releaseRow {versionName,versionCode,md5,size,changelog,downloadUrl}
 * 404 {error:"no_release"} -> nothing published yet (treated as up to date).
 * downloadUrl is server-relative and already carries ?versionCode=n, so the token is appended with &.
 */
class UpdateRepository(
    private val api: MusicPipeApi,
    private val downloadClient: OkHttpClient,
    private val context: Context,
    private val prefs: PrefsRepository
) {
    sealed class CheckResult {
        data class UpToDate(val currentVersionName: String) : CheckResult()
        data class UpdateAvailable(val release: LatestReleaseDto) : CheckResult()
        /** 404 no_release -> nothing published yet; treat as up to date with a hint. */
        data object NoRelease : CheckResult()
        data class Failed(val message: String, val retryable: Boolean = true) : CheckResult()
    }

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        /** MD5 mismatch -> file deleted, old app untouched, retry offered (§6). */
        data class ChecksumFailed(val expected: String, val actual: String) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    suspend fun checkForUpdate(currentVersionCode: Int, currentVersionName: String): CheckResult {
        return try {
            val latest = api.latestRelease().unwrap()
            if (VersionUtil.needsUpdate(currentVersionCode, latest.versionCode)) {
                CheckResult.UpdateAvailable(latest)
            } else {
                CheckResult.UpToDate(currentVersionName)
            }
        } catch (e: ApiException) {
            if (e.code == "no_release" || e.httpStatus == 404) CheckResult.NoRelease
            else CheckResult.Failed(e.message)
        } catch (t: Throwable) {
            CheckResult.Failed(t.message ?: "网络错误")
        }
    }

    /** Downloads to cacheDir/update/musicpipe-<vc>.apk with progress; verifies MD5 (§6). */
    suspend fun download(release: LatestReleaseDto, onProgress: (downloaded: Long, total: Long) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val target = File(dir, "musicpipe-${release.versionCode}.apk")
            // Reuse an already-complete, checksum-valid file (§6)
            if (target.exists() && target.length() == release.size && Md5.file(target) == release.md5.lowercase()) {
                return@withContext DownloadResult.Success(target)
            }
            target.delete()
            try {
                val request = Request.Builder().url(signedDownloadUrl(release.downloadUrl)).build()
                downloadClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext DownloadResult.Failed("下载失败 (HTTP ${resp.code})")
                    }
                    val body = resp.body ?: return@withContext DownloadResult.Failed("下载失败：空响应")
                    // -1 when neither stream nor release metadata knows the length -> indeterminate UI
                    val total = body.contentLength().takeIf { it > 0 } ?: release.size.takeIf { it > 0 } ?: -1L
                    var downloaded = 0L
                    val tmp = File(dir, target.name + ".part")
                    tmp.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                downloaded += n
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    if (!tmp.renameTo(target)) {
                        return@withContext DownloadResult.Failed("写入文件失败")
                    }
                    val actual = Md5.file(target)
                    if (actual != release.md5.lowercase()) {
                        target.delete()
                        return@withContext DownloadResult.ChecksumFailed(release.md5.lowercase(), actual)
                    }
                    DownloadResult.Success(target)
                }
            } catch (t: Throwable) {
                File(dir, target.name + ".part").delete()  // no stale partial downloads
                DownloadResult.Failed(t.message ?: "下载失败")
            }
        }

    /**
     * downloadUrl is server-relative ("/api/v1/app/releases/download?versionCode=2") and
     * already contains a query, so the token is appended with & by [ServerConfig.withToken].
     * The installer cannot send an Authorization header, hence the query parameter.
     */
    private suspend fun signedDownloadUrl(downloadUrl: String): String {
        ServerConfig.restBaseUrl = prefs.serverUrl.first()
        val absolute = ServerConfig.absolute(downloadUrl) ?: downloadUrl
        return ServerConfig.withToken(absolute, prefs.authToken.first())
    }

    /** Android 8+ unknown-source install permission (§6). */
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Launch the system installer for a verified APK via FileProvider (§6/§7). */
    fun installIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
