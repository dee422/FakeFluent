package com.dee.android.pbl.fakefluent

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("v1/chat/completions")
    suspend fun getChatResponse(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): ChatResponse
}

/**
 * 以下数据模型必须放在 interface 外部，
 * 这样 ChatViewModel 才能通过 "import com.dee.android.pbl.fakefluent.ChatRequest" 找到它们。
 */

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)