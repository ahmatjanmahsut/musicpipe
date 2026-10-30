package com.musicpipe.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.MusicPipeApp
import com.musicpipe.player.ui.theme.AppStyle
import com.musicpipe.player.ui.theme.MusicPipeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v1.8.0：给冷启动留一个可测量的锚点。AppContainer 的构造（Room/Retrofit/OkHttp）
        // 目前在主线程、且在 setContent 之前 —— 先量出来，别凭感觉优化。
        val appStart = SystemClock.uptimeMillis()
        val container = (application as MusicPipeApplication).container
        if (BuildConfig.DEBUG) {
            Log.d("MusicPipe", "AppContainer ready in " + (SystemClock.uptimeMillis() - appStart) + "ms")
        }
        setContent {
            // Android 13+: ask once for notification permission (media play-back notification)
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* granted or not, playback continues; notification is best-effort */ }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val themeMode by container.prefs.themeMode.collectAsState(initial = "system")
            // v1.13.0：界面风格。collectAsState 的初值是默认风格 —— DataStore 首帧还没回来时
            // 会短暂用 Editorial 渲染一次，随后切到用户选的风格。这是刻意的：
            // 读 DataStore 要等一次 IO，为它挡住首帧等于把冷启动白屏还回来（v1.8.0 刚修掉）。
            val styleId by container.prefs.appStyle.collectAsState(initial = "netease")
            val appStyle = remember(styleId) { AppStyle.fromId(styleId) }
            MusicPipeTheme(themeMode = themeMode, appStyle = appStyle) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    MusicPipeApp()
                }
            }
        }

        // v1.8.0 冷启动优化：把 MediaController 绑定（会创建 ExoPlayer）推到**首帧画出来之后**。
        // 用 OnPreDraw 而不是直接 post：onPreDraw 在首帧 measure/layout 完成、即将绘制时回调，
        // 此时让出主线程，ExoPlayer 的创建就落在首帧之后了。
        val decor = window.decorView
        decor.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                    container.startPlaybackEngine()
                    return true
                }
            }
        )
    }
}