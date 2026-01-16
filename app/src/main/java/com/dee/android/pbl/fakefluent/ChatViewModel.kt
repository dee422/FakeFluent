package com.dee.android.pbl.fakefluent

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

// UI 使用的消息模型
data class ChatMessageUI(
    val content: String,
    val isUser: Boolean
)

class ChatViewModel : ViewModel() {
    var chatMessages = mutableStateListOf<ChatMessageUI>()
        private set

    // 初始化时加入 System Prompt，优化回复风格
    private val apiChatHistory = mutableListOf(
        Message("system", "You are a helpful English speaking coach. Keep responses brief and natural.")
    )

    var isLoading by mutableStateOf(false)      // 网络请求中
    var isProcessing by mutableStateOf(false)   // AI 正在处理或朗读中
    var isSheetOpen by mutableStateOf(false)

    var currentProvider by mutableStateOf("SiliconFlow (Qwen)")
    var currentModel by mutableStateOf("Qwen/Qwen2.5-7B-Instruct")

    private var fetchJob: Job? = null
    private var tts: TextToSpeech? = null

    // 初始化 TTS 监听器，确保朗读完自动切换按钮
    fun setTTS(textToSpeech: TextToSpeech) {
        this.tts = textToSpeech
        this.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // 开始朗读，确保状态为 true
                isProcessing = true
            }

            override fun onDone(utteranceId: String?) {
                // 重点：朗读完成，切回发送键 (需回到主线程更新 UI)
                viewModelScope.launch { isProcessing = false }
            }

            override fun onError(utteranceId: String?) {
                viewModelScope.launch { isProcessing = false }
            }
        })
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        // 这里的 Key 和 URL 保持你之前的配置
        val config = when (currentProvider) {
            "SiliconFlow (Qwen)" -> "https://api.siliconflow.com/v1/" to "Bearer "
            "SiliconFlow (DeepSeek)" -> "https://api.siliconflow.com/v1/" to "Bearer "
            "Groq (国外)" -> "https://api.groq.com/openai/v1/" to "Bearer "
            "Gemini (国外)" -> "https://generativelanguage.googleapis.com/v1beta/openai/" to "Bearer "
            else -> "https://api.siliconflow.com/v1/" to ""
        }

        val (baseUrl, apiKey) = config

        chatMessages.add(ChatMessageUI(userText, true))
        apiChatHistory.add(Message("user", userText))

        fetchJob = viewModelScope.launch {
            isLoading = true
            try {
                val service = RetrofitClient.getService(baseUrl)
                val response = service.getChatResponse(apiKey, ChatRequest(currentModel, apiChatHistory))
                val aiReply = response.choices.firstOrNull()?.message?.content ?: "No response"

                chatMessages.add(ChatMessageUI(aiReply, false))
                apiChatHistory.add(Message("assistant", aiReply))

                // 网络请求结束
                isLoading = false
                // 开始朗读
                speakText(aiReply)

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    chatMessages.add(ChatMessageUI("Error: ${e.localizedMessage}", false))
                }
                isLoading = false
                isProcessing = false
            }
        }
    }

    // 封装朗读功能，带有 utteranceId 以触发监听器
    fun speakText(text: String) {
        isProcessing = true
        tts?.apply {
            setSpeechRate(0.9f) // 语速微调
            setPitch(1.05f)     // 音色微调
            // 传入 "CHAT_ID" 作为标识符，触发 onDone 回调
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "CHAT_ID")
        }
    }

    fun stopGeneration() {
        fetchJob?.cancel()
        tts?.stop() // 立即闭嘴
        isLoading = false
        isProcessing = false
    }

    fun clearHistory() {
        chatMessages.clear()
        // 清除历史但保留 System Prompt
        apiChatHistory.retainAll { it.role == "system" }
    }
}