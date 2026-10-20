package com.musicpipe.player.data.repo

import com.musicpipe.player.data.model.AppUser
import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.data.remote.ApiException
import com.musicpipe.player.data.remote.AuthTokens
import com.musicpipe.player.data.remote.MusicPipeApi
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.remote.dto.LoginRequest
import com.musicpipe.player.data.remote.dto.RegisterRequest
import com.musicpipe.player.data.remote.unwrap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Auth against the router-merged gateway (/api/v1/accounts/...).
 *
 *  - POST /api/v1/accounts/register -> 201 {id,username,role}, no session issued
 *  - POST /api/v1/accounts/login    -> {ok,token,expiresIn,id,username,role,user}
 *  - GET  /api/v1/accounts/me       -> restores the session on cold start (Bearer)
 *  - POST /api/v1/accounts/logout   -> invalidates the token server-side
 *
 * The bearer token is persisted under DataStore "auth_token" and mirrored in [AuthTokens]
 * so TokenInterceptor (OkHttp) and media3/Coil (?token=) share one source of truth.
 * The in-memory user drives nav-graph construction: admin routes are absent entirely for
 * role=user (requirement 1).
 */
class AuthRepository(
    private val api: MusicPipeApi,
    private val prefs: PrefsRepository
) {
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser

    /**
     * 「本地已能决定首屏去向」——**与网络无关**。
     *
     * v1.8.0 冷启动白屏修复。此前 UI 的门是 `restored = restoreSession() 返回之后才置 true`，
     * 而 [restoreSession] 内部要等 `/me` 网络往返（读超时 30s）。于是哪怕本地早已有身份，
     * 首屏仍被卡在 LoadingBlock 上，直到网络返回 —— 网络慢/无网时就是"白屏很久"。
     * 现在把"决定"与"校正"拆开：本地一有结论就置 true，网络只用于事后校正。
     */
    private val _restoreReady = MutableStateFlow(false)
    val restoreReady: StateFlow<Boolean> = _restoreReady

    /**
     * Synchronously seed ServerConfig + token from DataStore before any network call.
     * AppContainer collectors are async; without this, a cold-start me()/login could
     * run against the default host even though the user saved another one.
     */
    private suspend fun syncServerConfig() {
        ServerConfig.restBaseUrl = prefs.serverUrl.first().trimEnd('/')
        AuthTokens.set(prefs.authToken.first())
    }

    /**
     * Restore the persisted bearer session on cold start.
     *
     * 分两步，顺序很关键：
     * 1) **本地决定**（只读 DataStore，几十毫秒）：有 token + 有缓存身份 -> 立刻放行首屏；
     *    无 token -> 直接去登录页。到这一步 [restoreReady] 就置 true。
     * 2) **网络校正**（不阻塞首屏）：`/me` 回来后刷新身份/角色，401 则清态退回登录页。
     *
     * 只有"有 token 但本地没有缓存身份"这一种情况才需要等网络 —— 此时没有别的信息可用。
     */
    suspend fun restoreSession(): AppUser? {
        syncServerConfig()
        if (!AuthTokens.hasToken) {
            // No token stored -> nothing to restore; drop any stale cached identity.
            prefs.clearLastSession()
            _currentUser.value = null
            _restoreReady.value = true
            return null
        }
        val last = prefs.lastSession()
        // 角色本地可信：它是上次服务端 /me 的返回值，且所有管理端接口在服务端仍会二次校验，
        // 本地写死 role 不会越权。随后网络返回时再校正（角色被降级等情况）。
        val cached = last?.let { AppUser(it.userId, it.username, it.role) }
        // 首屏放行判定收敛到 StartupGate（纯函数、有单测），避免以后又被改回"等网络"。
        if (StartupGate.canShowUiWithoutNetwork(
                hasToken = AuthTokens.hasToken,
                hasCachedIdentity = cached != null
            )
        ) {
            _currentUser.value = cached
            // 关键：首屏在这里就可以放行了，不等下面的网络。
            _restoreReady.value = true
        }
        return try {
            val me = api.me().unwrap()
            val user = AppUser(me.id, me.username, me.role)
            _currentUser.value = user
            prefs.saveLastSession(user.id, user.username, user.role)
            _restoreReady.value = true
            user
        } catch (e: ApiException) {
            if (e.httpStatus == 401 || e.code == "unauthorized") {
                prefs.clearAuthToken()
                AuthTokens.clear()
                prefs.clearLastSession()
                _currentUser.value = null
                _restoreReady.value = true
                null
            } else {
                // Server reachable but failing: allow offline entry with the cached identity.
                offlineFallback(last)
            }
        } catch (_: java.io.IOException) {
            // Pure network failure with a cached identity: allow offline entry
            // (favourites/history are local; server calls surface errors individually).
            offlineFallback(last)
        } catch (_: Throwable) {
            // Unknown failure: keep whichever identity we already resolved (cached or null).
            _restoreReady.value = true
            _currentUser.value ?: cached
        }
    }

    private fun offlineFallback(last: PrefsRepository.LastSession?): AppUser? {
        val user = last?.let { AppUser(it.userId, it.username, it.role) }
        _currentUser.value = user
        // 离线放行同样是"已决定"，首屏不必再等。
        _restoreReady.value = true
        return user
    }

    /** POST accounts/register. No token is issued — callers chain [login] right after. */
    suspend fun register(username: String, password: String): AppUser {
        syncServerConfig()
        val dto = api.register(RegisterRequest(username, password)).unwrap()
        return AppUser(dto.id, dto.username, dto.role)
    }

    suspend fun login(username: String, password: String): AppUser {
        syncServerConfig()
        val dto = api.login(LoginRequest(username, password)).unwrap()
        val token = dto.token.ifBlank { throw IllegalStateException("登录响应缺少 token") }
        AuthTokens.set(token)
        prefs.setAuthToken(token)
        val fromUser = dto.user
        val user = AppUser(
            id = if (dto.id != 0L) dto.id else (fromUser?.id ?: 0L),
            username = dto.username.ifBlank { fromUser?.username ?: username },
            role = if (dto.role.isNotBlank()) dto.role else (fromUser?.role ?: "user")
        )
        _currentUser.value = user
        prefs.saveLastSession(user.id, user.username, user.role)
        return user
    }

    /** Register + login in one step: the register endpoint never yields a session. */
    suspend fun registerAndLogin(username: String, password: String): AppUser {
        register(username, password)
        return login(username, password)
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Throwable) { /* best effort — the token may already be dead */ }
        AuthTokens.clear()
        prefs.setAuthToken("")
        prefs.clearLastSession()
        _currentUser.value = null
    }
}
