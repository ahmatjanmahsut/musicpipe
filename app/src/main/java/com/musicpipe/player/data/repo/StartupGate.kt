package com.musicpipe.player.data.repo

/**
 * 冷启动「什么时候可以放行首屏」的唯一判定规则。
 *
 * v1.8.0 之前这条规则散在 UI 里，写法是「restoreSession() 全部跑完（含 /me 网络往返）
 * 才置 restored = true」—— 于是本地明明有身份，首屏也要陪着网络一起等，
 * 网络慢或无网时就是用户看到的「长时间白屏」。
 *
 * 现在规则收敛到这里，并且是纯函数：可以被单测锁住。如果有人把它改回「等网络」，
 * StartupGateTest 会失败，白屏回归不会悄悄发生。
 */
internal object StartupGate {

    /**
     * @param hasToken          本地是否存有 bearer token
     * @param hasCachedIdentity 本地是否存有上次登录的身份（id/username/role）
     *
     * 没有 token -> 必然去登录页，不需要网络。
     * 有 token 且有缓存身份 -> 直接进主界面，/me 作为事后校正。
     * 有 token 但没缓存身份 -> 无从判断角色，只能等网络（唯一必须等的情况）。
     */
    fun canShowUiWithoutNetwork(hasToken: Boolean, hasCachedIdentity: Boolean): Boolean =
        !hasToken || hasCachedIdentity
}
