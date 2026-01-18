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
    FRIEND("口语伙伴", """
        你是一个随和的英语口语伙伴。用地道的非正式英语和我聊天。
        如果我表达有误，请在回复最后委婉地提醒。
        格式：
        [你的自然回复]
        Correction: [地道表达] (简要说明)
    """.trimIndent()),

    COACH("专业外教", """
        你是一名专业且耐心的英语老师。重点纠正我的语法和表达地道性。
        ### 规则：
        1. 自然回复：先回答我的意思。
        2. 严格纠错：只要有表达不当，必须提供纠正。
        ### 格式：
        [你的回复]
        Correction: [更正后的句子] (语法点拨)
    """.trimIndent()),

    IELTS("雅思考官", """
        你是一名雅思口语考官。语气正式，会根据我的表达给出评估。
        在自然接话后，请为我刚才的句子给出一个参考分数和改进建议。
        格式：
        [你的回复]
        Correction: [高分表达] (Band Score: X & 提分建议)
    """.trimIndent()),

    TOEFL("托福考官", """
        你是一名托福口语老师。注重逻辑连接词和学术词汇的使用。
        请针对我的回答给出更具学术性或逻辑性的改写方案。
        格式：
        [你的回复]
        Correction: [学术化改写] (逻辑/词汇优化建议)
    """.trimIndent())
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    // 🚀 1. 初始化数据库和 DAO
    private val db = com.dee.android.pbl.fakefluent.db.AppDatabase.getDatabase(application)
    private val dao = db.favoriteWordDao()

    private val teacherPrompt = """
You are "FakeFluent Coach", a professional and encouraging English teacher. 
Your goal is to have a natural conversation with the user while subtly improving their English.

### RULES:
1. **Natural Response**: First, respond to the user's idea naturally (like a friend).
2. **Strict Correction**: If the user makes ANY grammar, spelling, or usage mistakes, provide a correction at the end.
3. **Format**: Use the exact format: 
   [Your natural response here]
   Correction: [Corrected sentence] (Briefly explain why in one simple sentence)

### EXAMPLE:
User: "I go to movie yesterday."
Coach: "Oh, that's nice! Which movie did you see?
Correction: I went to the movies yesterday. (Use the past tense 'went' for yesterday's actions.)"
""".trimIndent()

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

    var currentRole by mutableStateOf(CoachRole.COACH)
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

        // 1. 确保 System Prompt 始终是最新的
        if (apiChatHistory.isEmpty()) {
            apiChatHistory.add(Message("system", currentRole.systemPrompt))
        } else if (apiChatHistory[0].role == "system") {
            apiChatHistory[0] = Message("system", currentRole.systemPrompt)
        }

        // 2. 定义 baseUrl (确保它在 fetchJob 外部，让下面的代码能访问到)
        val baseUrl = when (currentProvider) {
            "Groq (国外)" -> "https://api.groq.com/openai/v1/"
            "Gemini (国外)" -> "https://generativelanguage.googleapis.com/v1beta/openai/"
            else -> "https://api.siliconflow.com/v1/"
        }

        // 3. 更新 UI 列表和历史记录
        chatMessages.add(ChatMessageUI(userText, true))
        apiChatHistory.add(Message("user", userText))

        // 4. 开启协程请求 AI
        fetchJob = viewModelScope.launch {
            isLoading = true
            val aiMsgIndex = chatMessages.size
            chatMessages.add(ChatMessageUI("...", false))
            var accumulatedText = ""

            try {
                val service = RetrofitClient.getService(baseUrl) // 🚀 这里现在能找到 baseUrl 了
                val responseBody = service.getChatResponseStream(
                    getEffectiveApiKey(),
                    ChatRequest(currentModel, apiChatHistory, stream = true)
                )

                withContext(Dispatchers.IO) {
                    responseBody.byteStream().bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            if (line.startsWith("data: ") && line.trim() != "data: [DONE]") {
                                try {
                                    val res = gson.fromJson(line.substring(6), ChatStreamResponse::class.java)
                                    val content = res.choices[0].delta.content ?: ""
                                    if (content.isNotEmpty()) {
                                        accumulatedText += content
                                        viewModelScope.launch(Dispatchers.Main) {
                                            if (aiMsgIndex < chatMessages.size) {
                                                chatMessages[aiMsgIndex] = ChatMessageUI(accumulatedText, false)
                                            }
                                        }
                                    }
                                } catch (e: Exception) { }
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
                isLoading = false
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