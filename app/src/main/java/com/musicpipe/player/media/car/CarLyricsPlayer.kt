package com.musicpipe.player.media.car

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 把"当前歌词行"注入到媒体元数据的播放器包装（v1.16.0，车机显示歌词的核心）。
 *
 * ## 为什么只能这么做
 *
 * 蓝牙 AVRCP 协议里**没有歌词字段**。车机屏幕上的一切文字（歌名/歌手/专辑）都来自
 * 手机推过去的媒体元数据（AVRCP 的 TITLE / ARTIST / ALBUM 属性），
 * 而 Android 的蓝牙栈是从 MediaSession 的元数据里取这三行的。
 * 所以"车机显示歌词"在系统能力范围内唯一的实现方式是：
 * **把当前歌词行写进这三行中的某一行**（放哪一行由用户按车机实际显示效果选）。
 *
 * ## 为什么包一层而不是改 MediaItem
 *
 * 改 MediaItem（replaceMediaItem）会触发列表与曲目变更事件，进而让 App 自己的
 * 历史记录/切歌逻辑误判"又换了一首歌"。包一层 [ForwardingPlayer] 只影响
 * [getMediaMetadata] 这个读取点：队列、进度、播放控制全部原样透传。
 *
 * ## 事件广播的坑（必须自己补）
 *
 * ForwardingPlayer 的 addListener 是把监听器转给**被包装的播放器**的，
 * 所以覆写 getMediaMetadata() 本身不会产生任何"元数据变了"的通知 ——
 * MediaSession 也就永远不会把新行推给蓝牙栈。
 * 这里自己留一份监听器名单，在注入文本变化时主动调一次 onMediaMetadataChanged，
 * 让 MediaSession 重新发布元数据。除此之外的所有事件仍然由被包装的播放器正常派发。
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CarLyricsPlayer(player: Player) : ForwardingPlayer(player) {

    private val listeners = CopyOnWriteArrayList<Player.Listener>()

    @Volatile private var line: String? = null
    @Volatile private var layout: CarLyricsLayout = CarLyricsLayout.TITLE
    @Volatile private var maxChars: Int = CarLyricsFormatter.DEFAULT_MAX_CHARS

    /**
     * 设置要注入的歌词行（null = 不注入，车机看到真实歌名）。
     * 文本没变就什么都不做 —— 每 200ms 都推一次元数据会让部分车机闪烁。
     */
    fun setInjection(line: String?, layout: CarLyricsLayout, maxChars: Int) {
        val changed = this.line != line || this.layout != layout || this.maxChars != maxChars
        this.line = line
        this.layout = layout
        this.maxChars = maxChars
        if (changed) dispatchMetadata()
    }

    override fun addListener(listener: Player.Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val text = line ?: return base
        val f = CarLyricsFormatter.fields(
            songTitle = base.title?.toString().orEmpty(),
            songArtist = base.artist?.toString().orEmpty(),
            songAlbum = base.albumTitle?.toString().orEmpty(),
            line = text,
            layout = layout,
            maxChars = maxChars
        )
        return base.buildUpon()
            .setTitle(f.title)
            .setArtist(f.artist)
            .setAlbumTitle(f.album)
            .build()
    }

    private fun dispatchMetadata() {
        val md = getMediaMetadata()
        listeners.forEach { l ->
            runCatching { l.onMediaMetadataChanged(md) }
        }
    }
}
