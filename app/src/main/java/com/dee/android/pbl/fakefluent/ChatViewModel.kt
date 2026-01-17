package com.dee.android.pbl.fakefluent

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.util.*

// 数据模型
data class ChatMessageUI(val content: String, val isUser: Boolean)
// 🚀 场景模型：新增 icon 字段
data class Scenario(val title: String, val prompt: String, val icon: String = "💬")

enum class CoachRole(val displayName: String, val systemPrompt: String) {
    DAILY_COACH("全能教练", "You are a helpful English speaking coach. Keep responses brief and natural."),
    TOEFL_EXAMINER("托福考官", "You are a professional TOEFL Speaking examiner. Respond briefly, then add a 'Correction:' section if needed."),
    CAMPUS_BUDDY("校园搭子", "You are a friendly American college student. Use campus slang.")
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("fake_fluent_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 状态管理
    var chatMessages = mutableStateListOf<ChatMessageUI>()
    var scenarios = mutableStateListOf<Scenario>()
        private set

    var currentRole by mutableStateOf(CoachRole.DAILY_COACH)
    var currentProvider by mutableStateOf("SiliconFlow (Qwen)")
    var currentModel by mutableStateOf("Qwen/Qwen2.5-7B-Instruct")
    var userApiKey by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var isProcessing by mutableStateOf(false)
    var isSheetOpen by mutableStateOf(false)

    private val apiChatHistory = mutableListOf<Message>()
    private var fetchJob: Job? = null
    private var tts: TextToSpeech? = null

    init {
        userApiKey = getCurrentSavedKey()
        loadScenarios()
    }

    // --- 角色切换 ---
    fun changeRole(role: CoachRole) {
        currentRole = role
        clearHistory()
    }

    // --- 场景持久化逻辑 ---
    private fun loadScenarios() {
        val json = prefs.getString("custom_scenarios_v2", null)
        if (json == null) {
            val defaultList = listOf(
                Scenario("Ordering Coffee", "I'm at a coffee shop. You are the barista.", "☕"),
                Scenario("Job Interview", "I'm applying for a job. You are the interviewer.", "💼"),
                Scenario("Ask for Directions", "I'm lost in London. Can you help me?", "🗺️"),
                Scenario("Daily Small Talk", "Let's just chat about our day.", "🏠")
            )
            scenarios.addAll(defaultList)
            saveScenariosToPrefs()
        } else {
            try {
                val type = object : TypeToken<List<Scenario>>() {}.type
                val list: List<Scenario> = gson.fromJson(json, type)
                scenarios.addAll(list)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun saveScenariosToPrefs() {
        val json = gson.toJson(scenarios)
        prefs.edit().putString("custom_scenarios_v2", json).apply()
    }

    fun addScenario(title: String, prompt: String, icon: String) {
        if (title.isNotBlank() && prompt.isNotBlank()) {
            scenarios.add(Scenario(title, prompt, if (icon.isBlank()) "✨" else icon))
            saveScenariosToPrefs()
        }
    }

    fun deleteScenario(scenario: Scenario) {
        scenarios.remove(scenario)
        saveScenariosToPrefs()
    }

    // --- API Key 管理 ---
    private fun getStorageKey() = "api_key_${currentProvider.replace(" ", "_").lowercase()}"
    fun getCurrentSavedKey() = prefs.getString(getStorageKey(), "") ?: ""
    fun saveApiKey(newKey: String) {
        prefs.edit().putString(getStorageKey(), newKey).apply()
        userApiKey = newKey
    }

    private fun getEffectiveApiKey(): String {
        val savedKey = getCurrentSavedKey()
        return if (savedKey.isNotBlank()) {
            if (savedKey.startsWith("Bearer ")) savedKey else "Bearer $savedKey"
        } else "Bearer YOUR_BACKUP_KEY"
    }

    // --- 核心对话逻辑 ---
    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        if (apiChatHistory.isEmpty()) apiChatHistory.add(Message("system", currentRole.systemPrompt))

        val baseUrl = when (currentProvider) {
            "Groq (国外)" -> "https://api.groq.com/openai/v1/"
            "Gemini (国外)" -> "https://generativelanguage.googleapis.com/v1beta/openai/"
            else -> "https://api.siliconflow.com/v1/"
        }

        chatMessages.add(ChatMessageUI(userText, true))
        apiChatHistory.add(Message("user", userText))

        fetchJob = viewModelScope.launch {
            isLoading = true
            val aiMsgIndex = chatMessages.size
            chatMessages.add(ChatMessageUI("", false))
            var accumulatedText = ""

            try {
                val service = RetrofitClient.getService(baseUrl)
                val responseBody = service.getChatResponseStream(getEffectiveApiKey(), ChatRequest(currentModel, apiChatHistory, stream = true))

                withContext(Dispatchers.IO) {
                    responseBody.byteStream().bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6).trim()
                                if (data != "[DONE]") {
                                    try {
                                        val res = gson.fromJson(data, ChatStreamResponse::class.java)
                                        val content = res.choices[0].delta.content ?: ""
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
                if (e !is CancellationException) {
                    chatMessages[aiMsgIndex] = ChatMessageUI("Error: ${e.localizedMessage}", false)
                }
            }
        }
    }

    fun setTTS(ttsInstance: TextToSpeech) {
        this.tts = ttsInstance
        this.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { viewModelScope.launch { isProcessing = true } }
            override fun onDone(id: String?) { viewModelScope.launch { isProcessing = false } }
            override fun onError(id: String?) { viewModelScope.launch { isProcessing = false } }
        })
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        val speech = text.split("Correction:")[0].trim()
        isProcessing = true
        tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "CHAT_ID")
    }

    fun stopGeneration() { fetchJob?.cancel(); tts?.stop(); isLoading = false; isProcessing = false }
    fun clearHistory() { chatMessages.clear(); apiChatHistory.clear() }
}