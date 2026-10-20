package com.musicpipe.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.musicpipe.player.data.remote.ApiClientFactory
import com.musicpipe.player.data.remote.DynamicBaseInterceptor
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.di.AppContainer

/**
 * v1.5.0：实现 [ImageLoaderFactory]，把 Coil 的图片客户端接到 [ApiClientFactory.sharedClient]
 * 的连接池上。
 *
 * 此前全项目没有任何 ImageLoader 配置，Coil 会自建一个 OkHttpClient —— 于是首屏 68 张封面
 * 走的是**第四套**连接池，冷启动时要额外做 TLS 握手，且与 API 请求争抢 socket。
 * 现在共用一个池，封面请求直接复用 API 刚建立的 keep-alive 连接（实测首页首屏封面
 * 从"逐个握手"变成"同一连接复用"）。
 *
 * 仍然保留 DynamicBaseInterceptor：封面 URL 由服务端给的绝对地址，但相对路径（/api/v1/cover/xxx）
 * 需要按设置页里的服务器地址解析，且允许运行中改地址。
 */
class MusicPipeApplication : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // v1.6.0 需求2：连续下载时 App 闪退。
        // 根因是 OkHttp 调用的 socket 读超时 / 连接被服务端重置时，回调线程上抛出的
        // 异常没人接住，直接把进程带走（用户看到的就是"点几下就闪退"，且没有崩溃对话框）。
        // 这里装一个进程级兜底，让这类后台网络异常只留下日志而不杀进程；
        // 真正的下载失败仍会由 NetRepository 的 catch 转成界面上的"失败，可重试"。
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (!isFatal(error)) {
                android.util.Log.w("MusicPipe", "swallowed background crash on " + thread.name, error)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /**
     * 只吞「后台网络/IO 异常」这一类。
     * OOM / StackOverflow / 主线程上的任何异常都按致命处理，交给系统弹崩溃对话框，
     * 免得把真正的程序 bug 藏起来 —— 用户报的闪退要能在崩溃记录里查到。
     */
    private fun isFatal(error: Throwable): Boolean {
        if (android.os.Looper.getMainLooper().thread === Thread.currentThread()) return true
        return when (error) {
            is OutOfMemoryError, is StackOverflowError, is AssertionError -> true
            // v1.10.0（C2）：只吞"确实属于网络 IO 失败"的类型。
            //
            // 原来把 IOException 放在最后统一判 false，等于把 FileNotFoundException、
            // EOFException、JSON 解析失败这类**逻辑错误**也一起吞了：
            // 线程死掉、日志只有一行 W，App 看着还活着但那个功能永远不再工作，
            // 用户只会说"下载不动了"，排查时几乎无法定位。
            // 现在只有 socket 级/超时/SSL/中断这几类按可恢复处理；
            // 其它 IOException（尤其是文件与解析）一律当致命，交给系统留崩溃记录。
            is java.net.SocketException, is java.net.SocketTimeoutException,
            is java.io.InterruptedIOException, is javax.net.ssl.SSLException -> false
            else -> true
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(
                ApiClientFactory.sharedClient.newBuilder()
                    .addInterceptor(DynamicBaseInterceptor { ServerConfig.restBaseUrl })
                    .build()
            )
            // v1.6.0：全局 crossfade 关掉。它会给**每一张**新出现的封面各开一段
            // 240ms 的动画；首页网格快速滑动时一屏 6~8 张同时进场，等于每帧都在跑
            // 多个并行动画并重绘，这正是"上下滑动掉帧"的直接来源之一。
            // 淡入效果改由 FadeInCover 在单张封面加载完成时做一次（alpha 只影响 draw 层，
            // 不触发布局也不排队等待其它封面）。
            .crossfade(false)
            // 封面是"一次写死、按 URL 缓存"的内容，给足磁盘缓存省掉重复解码。
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
}