package com.dee.android.pbl.fakefluent

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ApiService {
    // 基础非流式请求（保留以作兼容）
    @POST("chat/completions")
    suspend fun getChatResponse(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): ChatResponse

    // 流式输出请求：关键在于 @Streaming 注解和 ResponseBody 返回值
    @Streaming
    @POST("chat/completions")
    suspend fun getChatResponseStream(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): ResponseBody
}

/**
 * 请求体模型：增加了 stream 参数和默认参数
 */
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val stream: Boolean = false // 默认为 false，调用流式接口时需设为 true
)

data class Message(
    val role: String,
    val content: String
)

/**
 * 标准响应模型（非流式）
 */
data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

/**
 * 流式响应模型：解析 data: {...} 行时使用
 * 流式返回的结构中不是 "message" 而是 "delta"
 */
data class ChatStreamResponse(
    val choices: List<StreamChoice>
)

data class StreamChoice(
    val delta: Delta
)

data class Delta(
    val content: String? = null // 内容是增量跳出的
)