package com.dee.android.pbl.fakefluent

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class ChatMessageUI(val content: String, val isUser: Boolean)

class ChatViewModel : ViewModel() {
    var chatMessages = mutableStateListOf<ChatMessageUI>()
        private set

    private val apiChatHistory = mutableListOf<Message>()
    var isLoading by mutableStateOf(false)
    var isSheetOpen by mutableStateOf(false)

    // 默认使用 SiliconFlow，这样你不用翻墙也能用
    var currentProvider by mutableStateOf("SiliconFlow")
    var currentModel by mutableStateOf("deepseek-ai/DeepSeek-V3")

    private var tts: TextToSpeech? = null

    init {
        // 初始系统指令
        apiChatHistory.add(Message("system", "You are a helpful English coach. Correct my grammar first and then reply to me in natural English."))
    }

    fun setTTS(textToSpeech: TextToSpeech) { this.tts = textToSpeech }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        // 自动切换 URL 和 Key
        val (apiKey, baseUrl) = if (currentProvider == "SiliconFlow") {
            "Bearer " to "https://api.siliconflow.com/"
        } else {
            "Bearer " to "https://api.groq.com/openai/"
        }

        RetrofitClient.baseUrl = baseUrl
        chatMessages.add(ChatMessageUI(userText, true))
        apiChatHistory.add(Message("user", userText))

        viewModelScope.launch {
            isLoading = true
            try {
                // 每次请求前更新 Retrofit 实例以确保 baseUrl 生效
                val response = RetrofitClient.getService().getChatResponse(apiKey, ChatRequest(currentModel, apiChatHistory))
                val aiReply = response.choices.firstOrNull()?.message?.content ?: "No response from AI."

                chatMessages.add(ChatMessageUI(aiReply, false))
                apiChatHistory.add(Message("assistant", aiReply))
                speak(aiReply)
            } catch (e: Exception) {
                Log.e("ChatError", "Error: ${e.message}")
                chatMessages.add(ChatMessageUI("连接失败: ${e.localizedMessage}\n提示：请检查网络或切换 Provider", false))
            } finally {
                isLoading = false
            }
        }
    }

    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }

    fun clearHistory() {
        chatMessages.clear()
        apiChatHistory.retainAll { it.role == "system" }
    }
}