package com.dee.android.pbl.fakefluent

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import kotlinx.coroutines.delay
import java.util.*
import androidx.compose.material.icons.filled.CheckCircle

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                val voices = tts?.voices
                val naturalVoice = voices?.find {
                    it.name.contains("en-us-x-sfg") ||
                            it.name.contains("en-us-x-iom") ||
                            it.name.contains("network")
                }
                naturalVoice?.let { tts?.voice = it }
                chatViewModel.setTTS(tts!!)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F9FA)
                ) {
                    ChatScreen(chatViewModel)
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
fun ChatScreen(vm: ChatViewModel) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState()

    // 顺滑版自动滚动逻辑
    LaunchedEffect(vm.chatMessages.lastOrNull()?.content) {
        // 只有当 AI 正在输出内容（isLoading 或 isProcessing）时才触发
        if (vm.isLoading || vm.isProcessing) {
            if (vm.chatMessages.isNotEmpty()) {
                // 使用 animateScrollToItem 实现平滑滚动
                // 它会自动处理这种小幅度的增量滚动，看起来就像在被文字推着走
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("FakeFluent - ${vm.currentRole.displayName}", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.isSheetOpen = true }) {
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
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(vm.chatMessages) { message ->
                    ChatBubble(message) {
                        if (!message.isUser) {
                            vm.speakText(message.content)
                        }
                    }
                }

                // 底部占位，防止输入框遮挡
                if (vm.isLoading || vm.isProcessing) {
                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }

            // 底部输入栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
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

                if (vm.isLoading || vm.isProcessing) {
                    FloatingActionButton(
                        onClick = { vm.stopGeneration() },
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
                                vm.sendMessage(inputText)
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

        if (vm.isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { vm.isSheetOpen = false },
                sheetState = sheetState
            ) {
                SettingsContent(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(vm: ChatViewModel) {
    val providers = listOf(
        "SiliconFlow (Qwen)" to "Qwen/Qwen2.5-7B-Instruct",
        "SiliconFlow (DeepSeek)" to "deepseek-ai/DeepSeek-V3",
        "Groq (国外)" to "llama-3.3-70b-versatile",
        "Gemini (国外)" to "gemini-1.5-flash"
    )

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().padding(bottom = 32.dp)) {
        Text("练习场景 (Role Play)", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoachRole.entries.forEach { role ->
                FilterChip(
                    selected = vm.currentRole == role,
                    onClick = { vm.changeRole(role) },
                    label = { Text(role.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2196F3),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Text("AI 导师模型", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
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
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            // 解析文本：尝试将普通回复和纠错内容分开
            val parts = message.content.split("Correction:")
            val mainContent = parts[0].trim()
            val correction = if (parts.size > 1) parts[1].trim() else null

            // 1. 主消息气泡
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
                    .clickable(enabled = !isUser) { onClick() }
            ) {
                Text(
                    text = mainContent,
                    modifier = Modifier.padding(14.dp),
                    color = if (isUser) Color.White else Color.Black,
                    lineHeight = 22.sp
                )
            }

            // 2. 纠错小卡片（仅在 AI 回复且有错误时显示）
            if (correction != null && !isUser) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFFF3E0), // 温和的浅橘色背景
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.widthIn(max = 260.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 提示图标
                        Icon(
                            imageVector = Icons.Default.Settings, // 这里可以用 Settings 或自定义图标
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Teacher's Note:",
                                color = Color(0xFFE65100),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = correction,
                                color = Color(0xFF5D4037),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}