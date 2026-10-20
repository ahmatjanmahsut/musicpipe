package com.musicpipe.player.data.image

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musicpipe.player.data.repo.LyricsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * v1.7.0 需求2：预加载 / 离线缓存。
 *
 * 用户的原话是「加快 app 启动、上下滑动、以及音乐点开」。这三件事在实现上是三件不同的事：
 *
 *  启动 -> 曲库快照落盘，冷启动先渲染快照再被网络结果覆盖（见 HomeViewModel.init）
 *  滑动 -> 封面必须在滚动到之前就已在**内存缓存**里，否则每滑一屏都在等下载 + 解码
 *  点开 -> 歌词必须在点开之前就在内存里，否则进歌词页要等一次往返
 *
 * 本类负责后两件事：曲库加载完成后，在后台按顺序、低并发地把封面与歌词「喂」进各自的缓存。
 *
 * 关键设计约束（都是踩过的坑）：
 *  · **不能并发轰炸**：一台路由器同时处理几百张封面请求会拖慢用户当前正在做的事，
 *    所以串行 + 每张之间挂起让出，让用户操作永远优先。
 *  · **不在本地持有 Bitmap**：只让 Coil 写进它自己的 memory cache，本类不保存引用，
 *    避免预热自身变成内存压力。
 *  · **可被取消**：切服务器 / 退出登录时立刻停下，避免给旧地址做无用功。
 */
class Prewarmer(
    private val context: Context,
    private val lyricsRepository: LyricsRepository
) {

    private var job: Job? = null

    /** 取消正在进行的预热（改服务器 / 退出登录 / 重新加载时调用）。 */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * 曲库变化后调用：把封面与歌词预热进缓存。
     *
     * [covers] 是需要预取的封面绝对 URL（已带 token），[songIds] 用于歌词。
     * 两者都按 UI 里出现的先后顺序传入 —— 先预热的先被看到。
     */
    fun warm(scope: CoroutineScope, covers: List<String>, songIds: List<String>) {
        cancel()
        if (covers.isEmpty() && songIds.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            // 1) 歌词：轻量文本，批量读进内存 LRU（之后切歌 0ms）
            runCatching { lyricsRepository.prewarm(songIds.take(LYRICS_LIMIT)) }

            // 2) 封面：串行喂给 Coil。execute 会走完 network -> disk -> memory 全链路，
            //    于是下次真正显示时直接命中内存缓存（连解码都省了）。
            val loader = context.imageLoader
            for (url in covers.take(COVER_LIMIT)) {
                if (!isActive) return@launch
                runCatching {
                    loader.execute(
                        ImageRequest.Builder(context)
                            .data(url)
                            // 预热就按网格卡片的实际显示尺寸解码，避免缓存里塞原图
                            .size(COVER_DECODE_PX)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
                // 让出：把线程与带宽还给用户当前的点击/滚动/播放请求
                yield()
            }
        }
    }

    private companion object {
        /** 封面预取上限：一屏 6~8 张，预取 2~3 屏足够，太多反而拖慢首屏交互。 */
        const val COVER_LIMIT = 24

        /** 歌词预取上限：文本很小，直接覆盖整个曲库的量级。 */
        const val LYRICS_LIMIT = 200

        /** 与网格卡片拼图格的显示尺寸对齐（半屏宽卡片四分格 ≈ 200dp @2x）。 */
        const val COVER_DECODE_PX = 400
    }
}
