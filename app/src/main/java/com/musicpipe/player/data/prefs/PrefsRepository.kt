package com.musicpipe.player.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicpipe.player.media.car.CarLyricsLayout
import com.musicpipe.player.media.car.CarLyricsMode
import com.musicpipe.player.media.car.CarLyricsSettings
import com.musicpipe.player.media.eq.EqBands
import com.musicpipe.player.media.eq.EqPreset
import com.musicpipe.player.media.eq.EqPresetCodec
import com.musicpipe.player.media.eq.EqPresets
import com.musicpipe.player.media.eq.EqProfile
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
        val APP_STYLE = stringPreferencesKey("app_style")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")

        // ---- v1.16.0 均衡器（EQ）----
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        /** 空串 = 用户手动改过、不对应任何预设。 */
        val EQ_PRESET_ID = stringPreferencesKey("eq_preset_id")
        val EQ_GAINS = stringPreferencesKey("eq_gains")
        val EQ_MAKEUP_DB = floatPreferencesKey("eq_makeup_db")
        val EQ_CUSTOM = stringPreferencesKey("eq_custom_presets")
        val EQ_AUTO_SWITCH = booleanPreferencesKey("eq_auto_switch")

        // ---- v1.16.0 车载歌词 ----
        val CAR_MODE = stringPreferencesKey("car_lyrics_mode")
        val CAR_LAYOUT = stringPreferencesKey("car_lyrics_layout")
        val CAR_OFFSET = longPreferencesKey("car_lyrics_offset")
        val CAR_MAX_CHARS = longPreferencesKey("car_lyrics_max_chars")
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

    /**
     * 界面风格（AppStyle.id）。
     *
     * 默认 "netease" = v1.12.0 按 DESIGN.md 做的编辑风，也是用户当前看到的界面。
     * 取不到时由 AppStyle.fromId 兜底到默认风格，所以这里不需要再判空。
     */
    val appStyle: Flow<String> = store.data.map { it[Keys.APP_STYLE] ?: "netease" }

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

    /** v1.13.0：持久化界面风格。传入非 [AppStyle] 的 id 会被忽略成默认值。 */
    suspend fun setAppStyle(styleId: String) {
        val v = styleId.trim().ifEmpty { "netease" }
        store.edit { it[Keys.APP_STYLE] = v }
    }

    // ===================================================================
    // v1.16.0 新增
    // ===================================================================

    /** 均衡器总开关。 */
    val eqEnabled: Flow<Boolean> = store.data.map { it[Keys.EQ_ENABLED] ?: false }

    /**
     * 跟随输出设备自动切换预设。
     *
     * 默认 **关**：自动改设置会让"我明明选过，为什么又变了"发生。
     * 用户在设置里显式打开后才按"车机 → 车载预设 / 耳机 → 耳机预设"切换。
     */
    val eqAutoSwitch: Flow<Boolean> = store.data.map { it[Keys.EQ_AUTO_SWITCH] ?: false }

    /** 用户保存的自定义预设（内置预设不落盘，见 EqPresets.builtIns）。 */
    val eqCustomPresets: Flow<List<EqPreset>> = store.data.map {
        EqPresetCodec.decodeCustom(it[Keys.EQ_CUSTOM])
    }

    /**
     * 当前 EQ 曲线。
     *
     * 读取时的兜底顺序：显式存的曲线 → 预设自带曲线 → 平直。
     * 这样即使 DataStore 里只剩下一个 preset_id（例如老版本升级上来），
     * 也一定能给出一套合法的 5 段曲线。
     */
    val eqProfile: Flow<EqProfile> = store.data.map { p ->
        val presetId = p[Keys.EQ_PRESET_ID]?.takeIf { it.isNotBlank() }
        val preset = EqPresets.byId(presetId)
        val gains = p[Keys.EQ_GAINS]?.takeIf { it.isNotBlank() }?.let { EqPresetCodec.decodeGains(it) }
            ?: preset?.gains
            ?: EqBands.zero
        EqProfile(
            presetId = presetId,
            gains = EqBands.normalize(gains),
            makeupDb = EqBands.clampMakeup(p[Keys.EQ_MAKEUP_DB] ?: preset?.makeupDb ?: 0f)
        )
    }

    /** 车载歌词设置（模式/字段/延迟补偿/单行字数）。 */
    val carLyricsSettings: Flow<CarLyricsSettings> = store.data.map { p ->
        CarLyricsSettings(
            mode = CarLyricsMode.fromId(p[Keys.CAR_MODE]),
            layout = CarLyricsLayout.fromId(p[Keys.CAR_LAYOUT]),
            offsetMs = (p[Keys.CAR_OFFSET] ?: CarLyricsSettings().offsetMs).coerceIn(-2000L, 2000L),
            maxChars = (p[Keys.CAR_MAX_CHARS] ?: CarLyricsSettings().maxChars.toLong())
                .toInt().coerceIn(12, 120)
        )
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        store.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqAutoSwitch(on: Boolean) {
        store.edit { it[Keys.EQ_AUTO_SWITCH] = on }
    }

    /** 一次写入整套曲线（选预设 / 手动微调 / 存自定义预设都走这里）。 */
    suspend fun setEqProfile(profile: EqProfile) {
        val gains = EqBands.normalize(profile.gains)
        val makeup = EqBands.clampMakeup(profile.makeupDb)
        store.edit {
            it[Keys.EQ_PRESET_ID] = profile.presetId.orEmpty()
            it[Keys.EQ_GAINS] = EqPresetCodec.encodeGains(gains)
            it[Keys.EQ_MAKEUP_DB] = makeup
        }
    }

    suspend fun setEqCustomPresets(presets: List<EqPreset>) {
        store.edit { it[Keys.EQ_CUSTOM] = EqPresetCodec.encodeCustom(presets) }
    }

    suspend fun setCarLyricsMode(mode: CarLyricsMode) {
        store.edit { it[Keys.CAR_MODE] = mode.id }
    }

    suspend fun setCarLyricsLayout(layout: CarLyricsLayout) {
        store.edit { it[Keys.CAR_LAYOUT] = layout.id }
    }

    suspend fun setCarLyricsOffset(ms: Long) {
        store.edit { it[Keys.CAR_OFFSET] = ms.coerceIn(-2000L, 2000L) }
    }

    suspend fun setCarLyricsMaxChars(n: Int) {
        store.edit { it[Keys.CAR_MAX_CHARS] = n.coerceIn(12, 120).toLong() }
    }
}
