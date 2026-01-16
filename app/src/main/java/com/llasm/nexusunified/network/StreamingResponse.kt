package com.llasm.nexusunified.network

/**
 * 流式响应数据类
 */
data class StreamingResponse(
    val type: String,                    // 响应类型: text_update, complete, error, end
    val content: String? = null,         // 当前内容片段
    val full_text: String? = null,       // 完整文本
    val text: String? = null,            // 最终文本
    val sentence_count: Int? = null,     // 句子数量
    val message: String? = null,         // 错误消息
    val session_id: String? = null       // session_id（用于保存历史对话）
)
