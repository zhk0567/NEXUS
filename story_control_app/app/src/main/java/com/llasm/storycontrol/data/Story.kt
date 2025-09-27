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
    val category: String = "温馨故事"
)

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
