package com.llasm.storycontrol.ui

/**
 * 格式化时间显示
 */
fun formatTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds + 500) / 1000  // 四舍五入到最近的秒
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
