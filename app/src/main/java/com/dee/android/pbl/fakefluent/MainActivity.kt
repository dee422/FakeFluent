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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        setContent {
            // 使用 MaterialTheme 包裹，确保 Material3 组件能找到样式上下文
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF5F5F5)
                ) {
                    ChatScreen(speak = { text ->
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    })
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
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
    speak: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showTip by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    var isSheetOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 建议将 API Key 放在加密或安全位置，这里保持现状
    val myApiKey = "sk-uexdsdffmdxssoahsumbsbshmjepmehxuhrbxdevtczbmivm"

    // 监听消息列表长度，实现自动滚动和 AI 朗读
    LaunchedEffect(chatViewModel.chatHistory.size) {
        if (chatViewModel.chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatViewModel.chatHistory.size - 1)
            val lastMessage = chatViewModel.chatHistory.last()
            if (lastMessage.role == "assistant") {
                speak(lastMessage.content)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("FakeFluent Coach", fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { isSheetOpen = true }) {
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
                .imePadding() // 自动避开软键盘
                .padding(horizontal = 16.dp)
        ) {
            // 1. 聊天列表区域
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chatViewModel.chatHistory) { message ->
                    ChatBubble(message, onTextClick = { text -> speak(text) })
                }
            }

            // 2. 语音输入提示框
            if (showTip) {
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color(0xFFFFF9C4),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡 建议用输入法自带语音(选英)输入", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        IconButton(onClick = { showTip = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 3. 底部输入区
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Talk to me...") },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            chatViewModel.sendMessage(inputText, myApiKey)
                            inputText = ""
                        }
                    },
                    enabled = !chatViewModel.isLoading,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (chatViewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, null)
                    }
                }
            }
        }

        // 设置抽屉
        if (isSheetOpen) {
            ModalBottomSheet(onDismissRequest = { isSheetOpen = false }, sheetState = sheetState) {
                SettingsSheetContent(chatViewModel) { isSheetOpen = false }
            }
        }
    }
}

// 2. 找到 ChatBubble 函数定义
@Composable
fun ChatBubble(message: ChatMessage, onTextClick: (String) -> Unit) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            // 彻底删除 weight，回归最原始的自适应宽度，这是防止闪退的唯一办法
            modifier = Modifier
                .padding(4.dp)
                .clickable { onTextClick(message.content) },
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF007AFF) else Color.White
            )
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else Color.Black
            )
        }
    }
}

@Composable
fun SettingsSheetContent(chatViewModel: ChatViewModel, onClose: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
        Text("API Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Server Domain (Base URL)", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = chatViewModel.currentBaseUrl.contains("com"),
                onClick = { chatViewModel.currentBaseUrl = "https://api.siliconflow.com/" }
            )
            Text(".com")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = chatViewModel.currentBaseUrl.contains("cn"),
                onClick = { chatViewModel.currentBaseUrl = "https://api.siliconflow.cn/" }
            )
            Text(".cn")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text("AI Model", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = chatViewModel.currentModel.contains("DeepSeek"),
                    onClick = { chatViewModel.currentModel = "deepseek-ai/DeepSeek-V3" }
                )
                Text("DeepSeek-V3")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = chatViewModel.currentModel.contains("Qwen"),
                    onClick = { chatViewModel.currentModel = "Qwen/Qwen2.5-7B-Instruct" }
                )
                Text("Qwen-2.5")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Button(
            onClick = {
                chatViewModel.clearHistory()
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Clear Chat History", color = Color.White)
        }
    }
}