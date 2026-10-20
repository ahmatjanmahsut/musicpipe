package com.musicpipe.player.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.util.concurrent.TimeUnit

/**
 * 与服务器的延迟 / 带宽实测（v1.5.0 设置页「网络测试」）。
 *
 * 用户反馈"App 与后端连接太慢"时，需要一个能当场给出数字的东西，而不是让用户猜。
 * 这里做两组互不干扰的测量：
 *
 *   1. **延迟 RTT**：连续发 [RTT_ROUNDS] 次 GET /api/v1/system（响应仅 ~116B，几乎全是往返
 *      开销），取**中位数**而不是平均值 —— 偶发一次 300ms 抖动不该把结论带偏。
 *   2. **下载带宽**：对一首真实歌曲发 Range: bytes=0-[BENCH_BYTES-1]，只读这么多字节就断开，
 *      用"实际读到的字节 / 耗时"算 MB/s。Range 已被服务端和 123pan 双重验证支持（206），
 *      因此不会为了测速把整首 37MB 的 FLAC 全拉下来。
 *
 * 复用自己的 [client]（AppContainer 里传的是 [ApiClientFactory.sharedClient] 派生实例），
 * 这样测出来的就是 App 实际走的那条连接 —— 包含同一份 TLS 会话与连接池，
 * 数字对用户才有意义。
 *
 * 纯计算部分（[median]、[humanBytes]、[humanRate]、[formatSamples]）抽成顶层函数，
 * 便于 app\src\test 直接断言，不必起网络。
 */
object NetBench {

    /** 延迟测试轮数：3 次足够看出量级，又不至于让用户盯着转圈太久。 */
    const val RTT_ROUNDS = 3

    /** 带宽测试读取的字节上限：2MB 在 LAN 上约 20ms、在 4G 上约 2-4s，兼顾速度与准确度。 */
    const val BENCH_BYTES = 2L * 1024 * 1024

    data class RttResult(
        /** 每一轮的耗时（毫秒），按测量顺序。 */
        val samplesMs: List<Long>,
        /** 中位数，作为"延迟"展示。 */
        val medianMs: Long,
        /** 最小值，反映链路最好情况。 */
        val minMs: Long
    )

    data class SpeedResult(
        val bytesRead: Long,
        val elapsedMs: Long,
        val bytesPerSecond: Double
    ) {
        /** 人类可读速率，如 "12.4 MB/s" / "860 KB/s"。 */
        val humanReadable: String get() = humanRate(bytesPerSecond)
    }

    /**
     * 测 RTT：串行发 [RTT_ROUNDS] 次小请求。
     *
     * 串行而不是并发 —— 并发会把请求排在同一个连接上互相等，测出来的不是往返时间。
     * 每轮先拿系统时间再发请求，读完 body 立刻停表。
     */
    suspend fun measureRtt(
        client: OkHttpClient,
        baseUrl: String,
        rounds: Int = RTT_ROUNDS
    ): RttResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Long>()
        repeat(rounds) { index ->
            val started = System.nanoTime()
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/v1/system")
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    // 必须把 body 读完，否则测到的是"首字节时间"而不是"完整响应时间"
                    response.body?.bytes()
                }
            }
            samples += (System.nanoTime() - started) / 1_000_000
            // 轮间留一点间隔，避免三次请求被挤进同一次 TCP 往返里
            if (index < rounds - 1) delay(150)
        }
        RttResult(
            samplesMs = samples,
            medianMs = median(samples),
            minMs = samples.minOrNull() ?: 0L
        )
    }

    /**
     * 测下载带宽：用 Range 只拉前 [limitBytes] 字节。
     *
     * @param signedUrl 已经带好 token 的绝对 URL（与播放器用的是同一条 URL）。
     */
    suspend fun measureSpeed(
        client: OkHttpClient,
        signedUrl: String,
        limitBytes: Long = BENCH_BYTES
    ): SpeedResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(signedUrl)
            .header("Range", "bytes=0-" + (limitBytes - 1))
            .build()
        val started = System.nanoTime()
        var read = 0L
        client.newCall(request).execute().use { response ->
            val source = response.body?.source()
            if (source != null) {
                // 只读这么多就停：Range 生效时 body 本来就只有 limitBytes；万一服务端忽略
                // Range 返回完整文件，也绝不可能把 37MB 全灌进内存。
                val buffer = Buffer()
                while (read < limitBytes) {
                    val n = source.read(buffer, limitBytes - read)
                    if (n == -1L) break
                    read += n
                }
            }
        }
        val elapsedMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
        SpeedResult(
            bytesRead = read,
            elapsedMs = elapsedMs,
            bytesPerSecond = rate(read, elapsedMs)
        )
    }
}

/** 中位数。偶数个取偏小的那一个（3 次采样时即中间那次），空列表返回 0。 */
fun median(samples: List<Long>): Long {
    if (samples.isEmpty()) return 0L
    val sorted = samples.sorted()
    return sorted[sorted.size / 2]
}

/** 字节数 -> "1.5 MB" / "860 KB" / "512 B"。 */
fun humanBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> bytes.toString() + " B"
}

/** 速率 -> "12.4 MB/s" / "860 KB/s" / "512 B/s"。 */
fun humanRate(bytesPerSecond: Double): String = when {
    bytesPerSecond >= 1024.0 * 1024.0 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024.0 -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    else -> "%.0f B/s".format(bytesPerSecond)
}

/** 速率 = 字节 / 秒；耗时按毫秒，最小按 1ms 计避免除零得到 Infinity。 */
fun rate(bytes: Long, elapsedMs: Long): Double =
    bytes.toDouble() / (elapsedMs.coerceAtLeast(1).toDouble() / 1000.0)

/** 把每轮采样拼成 "12 / 15 / 88ms" 形式供 UI 展示。 */
fun formatSamples(samples: List<Long>): String =
    samples.joinToString(" / ") { it.toString() + "ms" }
