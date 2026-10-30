package com.musicpipe.player.ui.home

/**
 * v1.15.0：首页到底该显示什么。
 *
 * 抽成纯函数的原因很实际 —— 判断散在 when 分支里时没人能一眼看出四个组合各自意味着什么，
 * 而"启动后一直转圈"恰恰是这个判断写错造成的：
 *
 * 旧逻辑只有两条分支（loading && !hasContent -> 循环计数器；!hasContent -> 空态），
 * 而 refresh() 会在网络请求**之前**无条件置 loading=true。于是冷启动的必然路径是
 * "loading=true 且本地快照还没读完（hasContent=false）" → 命中循环计数器 → 一直转到
 * 网络返回为止。网络慢或服务器不可达时就是几十秒的 000→099 循环。
 *
 * 现在把"本地是否已经看过"（bootstrapped）作为独立维度：
 *  - 没看过本地：仍在启动阶段，短促的加载指示器合理（LoadingBlock 自带 220ms 延迟门）
 *  - 看过本地、确实没内容、在等网络：静止的同步态，**不动画**
 *  - 看过本地、有内容：直接出内容，refresh 只在顶部转细小的下拉指示器
 */
enum class HomeContentState {
    /** 还没读过本地快照，处于启动初期。可以显示加载指示器。 */
    BOOTSTRAPPING,

    /** 本地已读过、确实没有曲目，正在等网络。显示静止的同步态，不要循环动画。 */
    SYNCING_EMPTY,

    /** 没有任何内容，也不在加载：显示空态（可带错误提示）。 */
    EMPTY,

    /** 有内容，正常渲染列表/网格。 */
    CONTENT
}

object HomeLoadingGate {

    /**
     * @param loading     是否正在向服务器请求曲库
     * @param hasContent  是否已有可渲染的内容（songs 或 collections 非空）
     * @param bootstrapped 本地快照是否已经读完（无论快照是否为空）
     * @param hasOpenFolder 是否正处于某个文件夹的二级页面
     */
    fun resolve(
        loading: Boolean,
        hasContent: Boolean,
        bootstrapped: Boolean,
        hasOpenFolder: Boolean
    ): HomeContentState = when {
        // 二级页面不参与加载门：它渲染的是已打开文件夹里的曲目，与曲库刷新无关。
        hasOpenFolder -> HomeContentState.CONTENT
        hasContent -> HomeContentState.CONTENT
        loading && !bootstrapped -> HomeContentState.BOOTSTRAPPING
        loading -> HomeContentState.SYNCING_EMPTY
        else -> HomeContentState.EMPTY
    }
}
