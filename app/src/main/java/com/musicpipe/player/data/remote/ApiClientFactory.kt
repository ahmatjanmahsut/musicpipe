package com.musicpipe.player.data.remote

import com.musicpipe.player.data.prefs.PrefsRepository
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit/OkHttp wiring for the router gateway (docs/路由器整合报告.md §3–§5).
 * All clients share the dynamic base URL (settings page) + the Bearer token interceptor.
 *
 * v1.5.0 修复 App↔后端"连接慢"：
 *   1. 新增 [sharedClient]：API 与下载共用**同一个** OkHttpClient 实例，即共享同一连接池与
 *      同一份 TLS 会话。此前 API / 下载 / 封面(Coil 自建) 是三套独立连接池，冷启动要为
 *      同一台服务器做 3 次 TLS 握手（LAN 上每次约 20-40ms，WAN 上 60-120ms）。
 *   2. ConnectionPool(keepAlive 5 分钟, 8 条空闲)：默认只有 5 分钟 5 条，列表 + 封面突增时
 *      会立刻拆连接重建。首屏 68 首封面场景下差别明显。
 *   3. retryOnConnectionFailure(true) + pingInterval(30s)：Wi-Fi 切 4G、路由器 NAT 超时后
 *      的坏连接自动重试，不再让用户看到一次莫名的失败。
 */
object ApiClientFactory {

    /** 共享连接池：8 条空闲连接保活 5 分钟，够首屏列表 + 封面并发复用。 */
    private val connectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    /**
     * 全局唯一 OkHttpClient。所有走网关的请求（REST / 下载 / Coil 封面）都用它，
     * 从而共享连接池与 TLS 会话。
     */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * v1.6.0 需求2（连续下载时闪退）的客户端侧根因：
     *
     * 1. **静默重试写坏了请求体**。OkHttp 默认 retryOnConnectionFailure(true) 会在"连接已建立
     *    但请求体还没写完"时静默重发。下载入队的 POST 带 JSON body，重发会让服务端收到**两次**
     *    入队请求。用户连点几次 → 队列里堆出重复任务 → 并发下载把局域网和路由器打满。
     * 2. **连接被重置时线程上抛出未捕获异常**。连续下载时路由器侧 NAT / 容器重启会 RST 掉
     *    空闲 keep-alive 连接，OkHttp 的回调线程抛出 IOException，若此时没有活跃的
     *    try/catch（例如任务页轮询、主页 resume refresh 恰好在下一条请求的间隙），异常逃到
     *    线程池默认处理器 → 进程闪退，且因为不是主线程崩溃，连崩溃对话框都没有。
     *
     * 处理：下载客户端关闭静默重试（换成自己的幂等语义），并把两条超时都收紧到下载场景需要
     * 的量级；同时在 Application 里装了进程级兜底（见 MusicPipeApplication.onCreate）。
     */
    val downloadClient: OkHttpClient by lazy {
        sharedClient.newBuilder()
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * REST client for /api/v1 (library, lyrics, accounts, app releases, user admin).
     *
     * 以 [sharedClient] 为基底 newBuilder()：连接池与 TLS 会话共用，仅补上动态 base URL
     * 与 Bearer 头这两个拦截器。
     */
    fun musicPipeApi(prefs: PrefsRepository): MusicPipeApi {
        val client = sharedClient.newBuilder()
            .addInterceptor(DynamicBaseInterceptor { ServerConfig.restBaseUrl })
            .addInterceptor(TokenInterceptor { prefs.authTokenNow() })
            .build()
        return Retrofit.Builder()
            .baseUrl("http://placeholder.local/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicPipeApi::class.java)
    }

    /**
     * Plain OkHttp for the APK download (large body → long read timeout).
     *
     * 仍以 [sharedClient] 为基底，因此共享连接池；只把读超时放宽到 10 分钟，
     * 避免 20MB+ APK 在慢速 4G 上被 120s 截断。
     */
    fun downloadClient(prefs: PrefsRepository): OkHttpClient = sharedClient.newBuilder()
        .addInterceptor(DynamicBaseInterceptor { ServerConfig.restBaseUrl })
        .addInterceptor(TokenInterceptor { prefs.authTokenNow() })
        .readTimeout(10, TimeUnit.MINUTES)
        .build()
}