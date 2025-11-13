package com.llasm.storycontrol.data

import java.time.LocalDate

/**
 * 故事数据类
 */
data class Story(
    val id: String,
    val title: String,
    val content: String,
    val date: LocalDate,
    val category: String = "温馨故事",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val readingMode: ReadingMode = ReadingMode.TEXT
)

/**
 * 阅读模式枚举
 */
enum class ReadingMode(val displayName: String) {
    AUDIO("音频阅读"),
    TEXT("文字阅读")
}

/**
 * 故事类别
 */
enum class StoryCategory(val displayName: String) {
    WARM("温馨故事"),
    FAMILY("家庭故事"),
    FRIENDSHIP("友谊故事"),
    WISDOM("智慧故事"),
    TRADITIONAL("传统故事")
}
