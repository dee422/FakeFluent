package com.dee.android.pbl.fakefluent

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatViewModel : ViewModel() {
    val chatHistory = mutableStateListOf<ChatMessage>()
    var isLoading by mutableStateOf(false)
        private set

    // --- 新增：动态配置项 ---
    var currentBaseUrl by mutableStateOf("https://api.siliconflow.com/")
    var currentModel by mutableStateOf("deepseek-ai/DeepSeek-V3")

    // 动态获取 ApiService 的方法
    private fun getApiService(): ChatApiService {
        return Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApiService::class.java)
    }

    fun sendMessage(text: String, apiKey: String) {
        val cleanKey = apiKey.trim()
        val authHeader = "Bearer $cleanKey"

        chatHistory.add(ChatMessage(role = "user", content = text))

        viewModelScope.launch {
            isLoading = true
            try {
                val systemMsg = ChatMessage(
                    role = "system",
                    content = "You are an English coach. If the user makes grammar mistakes, point them out and provide corrections first. Then, reply in English."
                )

                // 使用当前选择的模型和 API 实例
                val response = getApiService().getChatCompletion(
                    authorization = authHeader,
                    request = ChatRequest(
                        model = currentModel,
                        messages = listOf(systemMsg) + chatHistory
                    )
                )

                response.choices.firstOrNull()?.message?.let {
                    chatHistory.add(it)
                }
            } catch (e: Exception) {
                chatHistory.add(ChatMessage("assistant", "Error: ${e.localizedMessage}"))
            } finally {
                isLoading = false
            }
        }
    }

    // 清空对话的方法
    fun clearHistory() {
        chatHistory.clear()
    }
}