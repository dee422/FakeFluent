package com.dee.android.pbl.fakefluent

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// 1. 数据模型（必须写在这里或者被 ChatViewModel 引用到）
data class ChatMessage(val role: String, val content: String)
data class ChatRequest(
    val model: String = "deepseek-ai/DeepSeek-V3", // 这里确保填入模型名称
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: ChatMessage)

// 2. 接口定义
interface ChatApiService {
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(
        // 注意：一定要写成 @Header("Authorization")，这才是服务器识别的键名
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse
}