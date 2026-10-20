package com.musicpipe.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * v1.10.0：把散落在各页的内联 `RoundedCornerShape(N.dp)` 收敛成常量。
 *
 * 之前 13 处直接写在 Composable 参数里（SongListItem / MiniPlayer / LoginScreen /
 * PlayerScreen / SearchScreen / AdminUsersScreen / TasksScreen / NetSearchScreen），
 * 每帧重组都会新建一个 RoundedCornerShape 实例，本身很轻，但会让该 Composable
 * 永远无法被跳过（参数恒为不等的新对象）。收敛成单例常量后既不新建对象，
 * 也让圆角规格在一处可查（Dso 规范：小图 4dp / 输入框 8dp / 药丸 20~24dp）。
 */
object DsoShapes {
    /** 列表/网格里的小封面、标签。 */
    val Small = RoundedCornerShape(4.dp)
    /** 输入框、小卡片。 */
    val Input = RoundedCornerShape(8.dp)
    /** 管理页小标签。 */
    val Badge = RoundedCornerShape(4.dp)
    /** 侧边小按钮。 */
    val SmallSurface = RoundedCornerShape(6.dp)
    /** 次级按钮 / 卡片。 */
    val Surface = RoundedCornerShape(10.dp)
    /** 搜索框、播放页控件。 */
    val Pill = RoundedCornerShape(20.dp)
    /** 登录页主按钮（24dp 圆角 = 高 48dp 的一半，呈药丸状）。 */
    val LoginButton = RoundedCornerShape(24.dp)
}
