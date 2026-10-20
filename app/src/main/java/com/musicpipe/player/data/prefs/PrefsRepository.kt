package com.musicpipe.player.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "musicpipe_prefs")

/**
 * DataStore-backed preferences. Router-merged contract: gateway URL, bearer token, theme.
 * The bearer token is additionally mirrored into a process-wide MutableStateFlow so OkHttp
 * interceptors can read it synchronously (media3 / Coil URLs carry ?token= instead).
 */
class PrefsRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val LAST_USER_ID = longPreferencesKey("last_user_id")
        val LAST_USERNAME = stringPreferencesKey("last_username")
        val LAST_ROLE = stringPreferencesKey("last_role")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    }

    companion object {
        // v1.3.0: 默认走公网域名 HTTPS(内网经 NAT reflection 回环, 外网直连), 登录页可改
        const val DEFAULT_SERVER_URL = "https://music.example.com"
        const val LAN_SERVER_URL = "http://192.168.1.10:7800"

        /** Process-wide mirror of the persisted bearer token (synchronous reads). */
        private val memoryToken = MutableStateFlow("")

        /**
         * 下载音质全程统一用 auto。
         *
         * 用户反馈「音质选择自动时提示：无法识别的音频头与文件头(mp3)与扩展名(flac)不符」——
         * 已修在服务端（runner 下载前嗅探真实容器并纠正扩展名），
         * 但客户端也不再提供"指定无损"这类会与音源实际能力冲突的选项，
         * 避免用户在 A 平台选 flac 而该平台只给 mp3 时再次踩坑。
         */
        const val DEFAULT_DOWNLOAD_QUALITY = "auto"

        /** Process-wide mirror so the download quality survives process death. */
        private val memoryQuality = MutableStateFlow(DEFAULT_DOWNLOAD_QUALITY)
    }

    private val store get() = context.dataStore

    val serverUrl: Flow<String> = store.data.map { it[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL }
    val authToken: Flow<String> = store.data.map { it[Keys.AUTH_TOKEN] ?: "" }
    val themeMode: Flow<String> = store.data.map { it[Keys.THEME_MODE] ?: "system" }

    /** Live bearer token, readable without suspending (see [memoryToken]). */
    val authTokenFlow: StateFlow<String> = memoryToken.asStateFlow()

    /** Synchronous snapshot for interceptors / URL signing. */
    fun authTokenNow(): String = memoryToken.value

    val downloadQuality: Flow<String> = store.data.map { it[Keys.DOWNLOAD_QUALITY] ?: DEFAULT_DOWNLOAD_QUALITY }

    fun downloadQualityNow(): String = memoryQuality.value

    suspend fun setDownloadQuality(q: String) {
        val v = q.trim().ifEmpty { DEFAULT_DOWNLOAD_QUALITY }
        store.edit { it[Keys.DOWNLOAD_QUALITY] = v }
        memoryQuality.value = v
    }

    suspend fun loadDownloadQuality() {
        memoryQuality.value = store.data.first()[Keys.DOWNLOAD_QUALITY] ?: DEFAULT_DOWNLOAD_QUALITY
    }

    /** Load the persisted token into memory once at startup. */
    suspend fun loadAuthToken(): String {
        val stored = store.data.first()[Keys.AUTH_TOKEN].orEmpty()
        memoryToken.value = stored
        return stored
    }

    data class LastSession(val userId: Long, val username: String, val role: String)

    suspend fun lastSession(): LastSession? {
        val p = store.data.first()
        val id = p[Keys.LAST_USER_ID] ?: return null
        return LastSession(id, p[Keys.LAST_USERNAME] ?: "", p[Keys.LAST_ROLE] ?: "user")
    }

    suspend fun saveLastSession(userId: Long, username: String, role: String) {
        store.edit {
            it[Keys.LAST_USER_ID] = userId
            it[Keys.LAST_USERNAME] = username
            it[Keys.LAST_ROLE] = role
        }
    }

    suspend fun clearLastSession() {
        store.edit {
            it.remove(Keys.LAST_USER_ID)
            it.remove(Keys.LAST_USERNAME)
            it.remove(Keys.LAST_ROLE)
            it.remove(Keys.AUTH_TOKEN)
        }
        memoryToken.value = ""
    }

    suspend fun setServerUrl(url: String) { store.edit { it[Keys.SERVER_URL] = url.trimEnd('/') } }

    /** Persist the bearer token (empty clears it) and refresh the in-memory mirror. */
    suspend fun setAuthToken(token: String) {
        val v = token.trim()
        store.edit { if (v.isEmpty()) it.remove(Keys.AUTH_TOKEN) else it[Keys.AUTH_TOKEN] = v }
        memoryToken.value = v
    }

    suspend fun clearAuthToken() { setAuthToken("") }

    suspend fun setThemeMode(mode: String) { store.edit { it[Keys.THEME_MODE] = mode } }
}