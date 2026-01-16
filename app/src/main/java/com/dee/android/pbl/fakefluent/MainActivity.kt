package com.dee.android.pbl.fakefluent

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS：优化口音，寻找高质量美式英语音色
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US

                // 尝试设置更地道的音色
                val voices = tts?.voices
                val naturalVoice = voices?.find {
                    it.name.contains("en-us-x-sfg") ||
                            it.name.contains("en-us-x-iom") ||
                            it.name.contains("network")
                }
                naturalVoice?.let { tts?.voice = it }
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F9FA)
                ) {
                    ChatScreen(tts = tts)
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel(), tts: TextToSpeech?) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState()

    // 将 Activity 的 TTS 传递给 ViewModel 管理
    LaunchedEffect(tts) {
        tts?.let { chatViewModel.setTTS(it) }
    }

    // 当有新消息时自动滚动到底部
    LaunchedEffect(chatViewModel.chatMessages.size) {
        if (chatViewModel.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatViewModel.chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("FakeFluent Coach", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { chatViewModel.isSheetOpen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // 聊天列表区域
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(chatViewModel.chatMessages) { message ->
                    // 增加点击气泡重读逻辑
                    ChatBubble(message) {
                        if (!message.isUser) {
                            chatViewModel.speakText(message.content)
                        }
                    }
                }
            }

            // 底部输入区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Speak English...") },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(Modifier.width(8.dp))

                // 核心逻辑：正在加载网络 或 正在朗读回复时，显示红色停止键
                if (chatViewModel.isLoading || chatViewModel.isProcessing) {
                    FloatingActionButton(
                        onClick = { chatViewModel.stopGeneration() },
                        containerColor = Color(0xFFFF5252),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Stop", tint = Color.White)
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                chatViewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        containerColor = Color(0xFF2196F3),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }

        if (chatViewModel.isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { chatViewModel.isSheetOpen = false },
                sheetState = sheetState
            ) {
                SettingsContent(chatViewModel)
            }
        }
    }
}

@Composable
fun SettingsContent(vm: ChatViewModel) {
    val providers = listOf(
        "SiliconFlow (Qwen)" to "Qwen/Qwen2.5-7B-Instruct",
        "SiliconFlow (DeepSeek)" to "deepseek-ai/DeepSeek-V3",
        "Groq (国外)" to "llama-3.3-70b-versatile",
        "Gemini (国外)" to "gemini-1.5-flash"
    )

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text("选择 AI 导师", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        providers.forEach { (name, modelId) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.currentProvider = name
                        vm.currentModel = modelId
                    }
                    .padding(vertical = 12.dp)
            ) {
                RadioButton(
                    selected = vm.currentProvider == name,
                    onClick = {
                        vm.currentProvider = name
                        vm.currentModel = modelId
                    }
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Text(modelId, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                vm.clearHistory()
                vm.isSheetOpen = false
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Clear Chat History", color = Color.White)
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessageUI, onClick: () -> Unit) {
    val isUser = message.isUser
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF007AFF) else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 16.dp
            ),
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                // 点击非用户气泡（即AI回复）时触发重读
                .clickable(enabled = !isUser) { onClick() }
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(14.dp),
                color = if (isUser) Color.White else Color.Black,
                lineHeight = 22.sp
            )
        }
    }
}