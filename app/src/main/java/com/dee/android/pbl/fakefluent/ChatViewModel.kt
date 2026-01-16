package com.dee.android.pbl.fakefluent

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

// UI 消息模型
data class ChatMessageUI(
    val content: String,
    val isUser: Boolean
)

// 定义场景角色
enum class CoachRole(val displayName: String, val systemPrompt: String) {
    DAILY_COACH("全能教练", "You are a helpful English speaking coach. Keep responses brief and natural."),

    // 🚀 重点修改：增加了纠错指令
    TOEFL_EXAMINER("托福考官",
        "You are a professional TOEFL Speaking examiner. " +
                "1. Respond to the user's content briefly. " +
                "2. At the end of your response, if the user made any grammar or word choice mistakes, " +
                "add a section starting with 'Correction:' and explain it simply. " +
                "If no mistakes, don't add the correction section."),

    CAMPUS_BUDDY("校园搭子", "You are a friendly American college student. Use campus slang. Help practice everyday life.")
}

class ChatViewModel : ViewModel() {
    private val gson = Gson()
    var chatMessages = mutableStateListOf<ChatMessageUI>()
        private set

    // 当前选中的角色
    var currentRole by mutableStateOf(CoachRole.DAILY_COACH)

    // API 历史记录
    private val apiChatHistory = mutableListOf<Message>()

    var isLoading by mutableStateOf(false)
    var isProcessing by mutableStateOf(false)
    var isSheetOpen by mutableStateOf(false)

    var currentProvider by mutableStateOf("SiliconFlow (Qwen)")
    var currentModel by mutableStateOf("Qwen/Qwen2.5-7B-Instruct")

    private var fetchJob: Job? = null
    private var tts: TextToSpeech? = null

    fun setTTS(textToSpeech: TextToSpeech) {
        this.tts = textToSpeech
        this.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { viewModelScope.launch { isProcessing = true } }
            override fun onDone(utteranceId: String?) { viewModelScope.launch { isProcessing = false } }
            override fun onError(utteranceId: String?) { viewModelScope.launch { isProcessing = false } }
        })
    }

    // 切换角色
    fun changeRole(role: CoachRole) {
        currentRole = role
        clearHistory()
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        // 如果是新对话，注入当前角色的 System Prompt
        if (apiChatHistory.isEmpty()) {
            apiChatHistory.add(Message("system", currentRole.systemPrompt))
        }

        val config = when (currentProvider) {
            "SiliconFlow (Qwen)" -> "https://api.siliconflow.com/v1/" to "Bearer sk-你的API Key"
            "SiliconFlow (DeepSeek)" -> "https://api.siliconflow.com/v1/" to "Bearer sk-你的API Key"
            "Groq (国外)" -> "https://api.groq.com/openai/v1/" to "Bearer 你的API Key"
            "Gemini (国外)" -> "https://generativelanguage.googleapis.com/v1beta/openai/" to "Bearer 你的API Key"
            else -> "https://api.siliconflow.com/v1/" to ""
        }
        val (baseUrl, apiKey) = config

        chatMessages.add(ChatMessageUI(userText, true))
        apiChatHistory.add(Message("user", userText))

        fetchJob = viewModelScope.launch {
            isLoading = true
            val aiMsgIndex = chatMessages.size
            chatMessages.add(ChatMessageUI("", false))
            var accumulatedText = ""

            try {
                val service = RetrofitClient.getService(baseUrl)
                val responseBody = service.getChatResponseStream(
                    apiKey,
                    ChatRequest(currentModel, apiChatHistory, stream = true)
                )

                withContext(Dispatchers.IO) {
                    responseBody.byteStream().bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6).trim()
                                if (data != "[DONE]") {
                                    try {
                                        val streamRes = gson.fromJson(data, ChatStreamResponse::class.java)
                                        val content = streamRes.choices.firstOrNull()?.delta?.content ?: ""
                                        if (content.isNotEmpty()) {
                                            accumulatedText += content
                                            viewModelScope.launch {
                                                chatMessages[aiMsgIndex] = ChatMessageUI(accumulatedText, false)
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
                apiChatHistory.add(Message("assistant", accumulatedText))
                isLoading = false
                speakText(accumulatedText)
            } catch (e: Exception) {
                isLoading = false
                isProcessing = false
                if (e !is CancellationException) {
                    chatMessages[aiMsgIndex] = ChatMessageUI("Error: ${e.localizedMessage}", false)
                }
            }
        }
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        isProcessing = true
        tts?.apply {
            setSpeechRate(0.9f)
            setPitch(1.05f)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "CHAT_ID")
        }
    }

    fun stopGeneration() {
        fetchJob?.cancel()
        tts?.stop()
        isLoading = false
        isProcessing = false
    }

    fun clearHistory() {
        chatMessages.clear()
        apiChatHistory.clear()
    }
}