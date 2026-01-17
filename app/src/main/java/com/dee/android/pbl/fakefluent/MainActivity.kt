package com.dee.android.pbl.fakefluent

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                chatViewModel.setTTS(tts!!)
            }
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) {
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
    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newPrompt by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf("✨") }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 自动滚动到底部逻辑
    LaunchedEffect(vm.chatMessages.size) {
        if (vm.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(vm.chatMessages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Scenarios", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") }
                }
                HorizontalDivider()
                LazyColumn {
                    items(vm.scenarios) { scenario ->
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(scenario.icon, modifier = Modifier.padding(end = 12.dp))
                                    Text(scenario.title, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { vm.deleteScenario(scenario) }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; inputText = scenario.prompt },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    title = { Text("FakeFluent", fontWeight = FontWeight.Black) },
                    actions = { IconButton(onClick = { vm.isSheetOpen = true }) { Icon(Icons.Default.Settings, null) } }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
                // 消息列表
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = 16.dp)) {
                    items(vm.chatMessages) { ChatBubble(it) { if (!it.isUser) vm.speakText(it.content) } }
                }

                // --- 底部输入区域 ---
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()) {
                    TextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("Type English...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    // 🚀 停止键逻辑：只有在加载文字(isLoading)或播放语音(isProcessing)时出现
                    if (vm.isLoading || vm.isProcessing) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE))
                                .clickable { vm.stopGenerating() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stop, "Stop AI", tint = Color.Red)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // 发送键始终存在
                    FloatingActionButton(
                        onClick = { if (inputText.isNotBlank()) { vm.sendMessage(inputText); inputText = "" } },
                        containerColor = if (inputText.isBlank()) Color.LightGray else Color(0xFF2196F3),
                        shape = CircleShape, modifier = Modifier.size(48.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send Message", tint = Color.White) }
                }
            }
        }
    }

    // --- 对话框区域 ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Scenario") },
            text = {
                Column {
                    // 1. 图标输入 (Emoji)
                    OutlinedTextField(
                        value = newIcon,
                        onValueChange = { newIcon = it },
                        label = { Text("Icon (Emoji)") },
                        placeholder = { Text("e.g. ✈️") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    // 2. 场景名称
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Scenario Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    // 3. AI Prompt
                    OutlinedTextField(
                        value = newPrompt,
                        onValueChange = { newPrompt = it },
                        label = { Text("Initial Prompt") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    // 确保图标不为空，否则给个默认的 ✨
                    val finalIcon = if (newIcon.isBlank()) "✨" else newIcon
                    vm.addScenario(newTitle, newPrompt, finalIcon)

                    // 重置所有输入框状态
                    showAddDialog = false
                    newTitle = ""
                    newPrompt = ""
                    newIcon = "✨"
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (vm.isSheetOpen) ModalBottomSheet(onDismissRequest = { vm.isSheetOpen = false }) { SettingsContent(vm) }
}

@Composable
fun ChatBubble(message: ChatMessageUI, onSpeak: () -> Unit) {
    val isUser = message.isUser
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            val parts = message.content.split("Correction:")
            Surface(
                color = if (isUser) Color(0xFF007AFF) else Color.White,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.widthIn(max = 280.dp).clickable(enabled = !isUser) { onSpeak() }
            ) {
                Text(text = parts[0].trim(), modifier = Modifier.padding(12.dp), color = if (isUser) Color.White else Color.Black)
            }
            // 纠错卡片逻辑
            if (parts.size > 1 && !isUser) {
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE082)),
                    modifier = Modifier.padding(top = 6.dp).widthIn(max = 260.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Coach's Suggestion", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                        Text(text = parts[1].trim(), fontSize = 13.sp, color = Color(0xFF5D4037))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(vm: ChatViewModel) {
    var showKeyDialog by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf("") }
    val providers = listOf(
        "SiliconFlow (Qwen)" to "Qwen/Qwen2.5-7B-Instruct",
        "SiliconFlow (DeepSeek)" to "deepseek-ai/DeepSeek-V3",
        "Groq (国外)" to "llama-3.3-70b-versatile",
        "Gemini (国外)" to "gemini-1.5-flash"
    )

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().padding(bottom = 32.dp)) {
        Text("My API Key", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), onClick = { inputKey = vm.getCurrentSavedKey(); showKeyDialog = true }) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(vm.currentProvider, fontWeight = FontWeight.Bold)
                    val key = vm.getCurrentSavedKey()
                    Text(if (key.isEmpty()) "Not Set" else "••••" + key.takeLast(4), color = Color.Gray, fontSize = 12.sp)
                }
                Icon(Icons.Default.Edit, null)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text("Coach Role", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoachRole.entries.forEach { role ->
                FilterChip(selected = vm.currentRole == role, onClick = { vm.changeRole(role) }, label = { Text(role.displayName) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("AI Model", fontWeight = FontWeight.Bold)
        providers.forEach { (name, model) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.currentProvider = name; vm.currentModel = model }.padding(vertical = 8.dp)) {
                RadioButton(selected = vm.currentProvider == name, onClick = { vm.currentProvider = name; vm.currentModel = model })
                Column(Modifier.padding(start = 8.dp)) { Text(name); Text(model, fontSize = 12.sp, color = Color.Gray) }
            }
        }
        Button(onClick = { vm.clearHistory(); vm.isSheetOpen = false }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) { Text("Clear History") }
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("Set API Key") },
            text = { OutlinedTextField(value = inputKey, onValueChange = { inputKey = it }, placeholder = { Text("sk-...") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { vm.saveApiKey(inputKey); showKeyDialog = false }) { Text("Save") } }
        )
    }
}