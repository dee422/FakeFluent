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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.stateIn
import java.util.*
import kotlinx.coroutines.flow.first

data class ChatMessageUI(val content: String, val isUser: Boolean)
data class Scenario(val title: String, val prompt: String, val icon: String = "💬")

enum class CoachRole(val displayName: String, val systemPrompt: String) {
    DAILY_COACH("全能教练", "You are a helpful English speaking coach. Keep responses brief and natural."),
    TOEFL_EXAMINER("托福考官", "You are a professional TOEFL Speaking examiner. Respond briefly, then add a 'Correction:' section if needed."),
    CAMPUS_BUDDY("校园搭子", "You are a friendly American college student. Use campus slang.")
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    // 🚀 1. 初始化数据库和 DAO
    private val db = com.dee.android.pbl.fakefluent.db.AppDatabase.getDatabase(application)
    private val dao = db.favoriteWordDao()

    var isNotebookOpen by mutableStateOf(false)

    // 🚀 2. 定义收藏列表的 StateFlow
    // 这里直接使用这种写法，最稳定，不需要额外的扩展函数
    val favoriteWords: kotlinx.coroutines.flow.StateFlow<List<com.dee.android.pbl.fakefluent.db.FavoriteWord>> =
        dao.getAllWords().stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 🚀 3. 添加收藏/取消收藏逻辑
    fun toggleFavorite(text: String, correction: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 先检查数据库里是否已经有这个文本
            if (dao.isFavorite(text)) {
                // 2. 如果有，我们需要查出所有的词，找到匹配的那一个并删除
                // 注意：这里我们通过 originalText 来匹配
                val allFavs = dao.getAllWords().first() // 获取当前列表的第一帧数据
                val itemToDelete = allFavs.find { it.originalText == text }
                itemToDelete?.let {
                    dao.delete(it)
                }
            } else {
                // 3. 如果没有，则执行插入
                val newFav = com.dee.android.pbl.fakefluent.db.FavoriteWord(
                    originalText = text,
                    correction = correction,
                    scene = currentRole.displayName
                )
                dao.insert(newFav)
            }
        }
    }
    private val prefs = application.getSharedPreferences("fake_fluent_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var chatMessages = mutableStateListOf<ChatMessageUI>()
    var scenarios = mutableStateListOf<Scenario>()
        private set

    var currentRole by mutableStateOf(CoachRole.DAILY_COACH)
    var currentProvider by mutableStateOf("SiliconFlow (Qwen)")
    var currentModel by mutableStateOf("Qwen/Qwen2.5-7B-Instruct")
    var userApiKey by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var isProcessing by mutableStateOf(false) // 专门用于 TTS 状态
    var isSheetOpen by mutableStateOf(false)

    private val apiChatHistory = mutableListOf<Message>()
    private var fetchJob: Job? = null
    private var tts: TextToSpeech? = null

    init {
        userApiKey = getCurrentSavedKey()
        loadScenarios()
    }

    fun changeRole(role: CoachRole) { currentRole = role; clearHistory() }

    private fun loadScenarios() {
        val json = prefs.getString("custom_scenarios_v2", null)
        if (json == null) {
            val defaultList = listOf(
                Scenario("Ordering Coffee", "I'm at a coffee shop. You are the barista.", "☕"),
                Scenario("Job Interview", "I'm applying for a job. You are the interviewer.", "💼"),
                Scenario("Ask for Directions", "I'm lost in London. Can you help me?", "🗺️"),
                Scenario("Daily Small Talk", "Let's just chat about our day.", "🏠")
            )
            scenarios.addAll(defaultList); saveScenariosToPrefs()
        } else {
            try {
                val list: List<Scenario> = gson.fromJson(json, object : TypeToken<List<Scenario>>() {}.type)
                scenarios.addAll(list)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun saveScenariosToPrefs() {
        prefs.edit().putString("custom_scenarios_v2", gson.toJson(scenarios)).apply()
    }

    fun addScenario(title: String, prompt: String, icon: String) {
        if (title.isNotBlank() && prompt.isNotBlank()) {
            scenarios.add(Scenario(title, prompt, if (icon.isBlank()) "✨" else icon))
            saveScenariosToPrefs()
        }
    }

    fun deleteScenario(scenario: Scenario) { scenarios.remove(scenario); saveScenariosToPrefs() }

    private fun getStorageKey() = "api_key_${currentProvider.replace(" ", "_").lowercase()}"
    fun getCurrentSavedKey() = prefs.getString(getStorageKey(), "") ?: ""
    fun saveApiKey(newKey: String) { prefs.edit().putString(getStorageKey(), newKey).apply(); userApiKey = newKey }

    private fun getEffectiveApiKey(): String {
        val savedKey = getCurrentSavedKey()
        return if (savedKey.isNotBlank()) {
            if (savedKey.startsWith("Bearer ")) savedKey else "Bearer $savedKey"
        } else ""
    }

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
            isLoading = true // 🚀 开启显示停止键
            val aiMsgIndex = chatMessages.size
            chatMessages.add(ChatMessageUI("...", false))
            var accumulatedText = ""

            try {
                val service = RetrofitClient.getService(baseUrl)
                val responseBody = service.getChatResponseStream(getEffectiveApiKey(), ChatRequest(currentModel, apiChatHistory, stream = true))

                withContext(Dispatchers.IO) {
                    responseBody.byteStream().bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ") && line.trim() != "data: [DONE]") {
                                try {
                                    val res = gson.fromJson(line.substring(6), ChatStreamResponse::class.java)
                                    val content = res.choices[0].delta.content ?: ""
                                    if (content.isNotEmpty()) {
                                        accumulatedText += content
                                        // 🚀 修复点：直接使用 viewModelScope.launch 而不是 withContext
                                        viewModelScope.launch(Dispatchers.Main) {
                                            if (aiMsgIndex < chatMessages.size) {
                                                chatMessages[aiMsgIndex] = ChatMessageUI(accumulatedText, false)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略单行解析错误
                                }
                            }
                        }
                    }
                }
                apiChatHistory.add(Message("assistant", accumulatedText))
                speakText(accumulatedText)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        chatMessages[aiMsgIndex] = ChatMessageUI("Error: ${e.localizedMessage}", false)
                    }
                }
            } finally {
                isLoading = false // 🚀 完成或取消，停止键消失
            }
        }
    }

    fun setTTS(ttsInstance: TextToSpeech) {
        this.tts = ttsInstance
        this.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { isProcessing = true }
            override fun onDone(id: String?) { isProcessing = false }
            override fun onError(id: String?) { isProcessing = false }
        })
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        val speech = text.split("Correction:")[0].trim()
        tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "CHAT_ID")
    }

    fun stopGenerating() {
        fetchJob?.cancel()
        fetchJob = null
        tts?.stop()
        isLoading = false
        isProcessing = false
        if (chatMessages.isNotEmpty() && chatMessages.last().content == "...") {
            chatMessages.removeAt(chatMessages.size - 1)
        }
    }

    fun clearHistory() { chatMessages.clear(); apiChatHistory.clear(); stopGenerating() }
}