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
import androidx.compose.material.icons.automirrored.filled.ArrowBack

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    LaunchedEffect(vm.chatMessages.size) {
        if (vm.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(vm.chatMessages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // 1. 标题栏
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("FakeFluent", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }

                // 🚀 建议把 Notebook 放在这里（顶部），作为一个固定功能
                NavigationDrawerItem(
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Book, null, modifier = Modifier.padding(end = 12.dp))
                            Text("My Notebook", fontWeight = FontWeight.Bold)
                        }
                    },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        vm.isNotebookOpen = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 2. 场景列表标题
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Scenarios", color = Color.Gray, fontSize = 14.sp)
                    IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") }
                }

                // 3. 场景列表 (LazyColumn)
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
                            onClick = {
                                scope.launch { drawerState.close() }
                                inputText = scenario.prompt
                            },
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
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = 16.dp)) {
                    // 🚀 核心改动：当聊天记录为空时，显示开发者宣言
                    if (vm.chatMessages.isEmpty()) {
                        item {
                            VibeCodingIntro()
                        }
                    }
                    items(vm.chatMessages) { messageUI ->
                        ChatBubble(
                            message = messageUI,
                            vm = vm,
                            onSpeak = { if (!messageUI.isUser) vm.speakText(messageUI.content) }
                        )
                    }
                }

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

                    if (vm.isLoading || vm.isProcessing) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFFEBEE)).clickable { vm.stopGenerating() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stop, "Stop AI", tint = Color.Red)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

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

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Scenario") },
            text = {
                Column {
                    OutlinedTextField(value = newIcon, onValueChange = { newIcon = it }, label = { Text("Icon (Emoji)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Scenario Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newPrompt, onValueChange = { newPrompt = it }, label = { Text("Initial Prompt") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addScenario(newTitle, newPrompt, if (newIcon.isBlank()) "✨" else newIcon)
                    showAddDialog = false; newTitle = ""; newPrompt = ""; newIcon = "✨"
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
    if (vm.isSheetOpen) ModalBottomSheet(onDismissRequest = { vm.isSheetOpen = false }) { SettingsContent(vm) }
    if (vm.isNotebookOpen) {
        NotebookScreen(vm)
    }
}

@Composable
fun VibeCodingIntro() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FakeFluent", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF2196F3))
        Text("A Vibe Coding Project", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        Surface(
            color = Color(0xFFE3F2FD),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("授人以鱼，不如授人以渔。", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2), fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "这不是一款标准的商业应用，而是一场关于“创造”的实验。我拒绝将其上架商店，因为比起直接给你一个工具，我更想邀你一起体验亲手构建它的乐趣。",
                    fontSize = 14.sp, lineHeight = 22.sp, color = Color.DarkGray
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "如果你想拥有它，欢迎联系我，我会教你如何搭建它。欢迎开启你的编程之旅。",
                    fontSize = 14.sp, lineHeight = 22.sp, color = Color.DarkGray
                )
                Spacer(Modifier.height(16.dp))
                Text("📬 jd1370791@gmail.com", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1976D2))
            }
        }
    }
}

// ✅ 修复点 2：将 ChatBubble 移出 ChatScreen，确保它是顶层函数
@Composable
fun ChatBubble(message: ChatMessageUI, vm: ChatViewModel, onSpeak: () -> Unit) {
    val isUser = message.isUser
    // 🚀 新增：从 ViewModel 观察收藏列表，判断当前文本是否在其中
    val favorites by vm.favoriteWords.collectAsState()
    val isFav = favorites.any { it.originalText == message.content.split("Correction:")[0].trim() }

    Row( /* ...原有代码... */ ) {
        if (!isUser) {
            IconButton(
                onClick = {
                    val parts = message.content.split("Correction:")
                    vm.toggleFavorite(parts[0].trim(), parts.getOrNull(1)?.trim() ?: "")
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    // 🚀 修改：如果是已收藏，显示实心星星，否则显示空心
                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    // 🚀 修改：已收藏显示金色，未收藏显示灰色
                    tint = if (isFav) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

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

        // 🚀 核心改动：API 配置指南
        Surface(
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text("配置指南与费用", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "• SiliconFlow: 国内直连，注册即送免费额度。DeepSeek-V3 性价比极高。\n" +
                            "• Groq/Gemini: 需科学上网环境。Groq 极速，Gemini 有优秀免费层。\n" +
                            "• 隐私: Key 仅保存在手机本地，绝不上传。",
                    fontSize = 12.sp, lineHeight = 18.sp, color = Color.Gray
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(vm: ChatViewModel) {
    // 实时观察收藏列表
    val favorites by vm.favoriteWords.collectAsState(initial = emptyList())

    // 使用 Surface 确保背景不透明，盖住下方的聊天界面
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Notebook", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { vm.isNotebookOpen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Book, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("Your notebook is empty", color = Color.Gray)
                    Text("Tap ⭐ in chat to save phrases.", fontSize = 12.sp, color = Color.LightGray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favorites) { word ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                // 🚀 保留全卡片点击，涟漪效果会让反馈很直观
                                .clickable { vm.speakText(word.originalText) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp), // 稍微降低阴影，显得更扁平现代
                            shape = RoundedCornerShape(16.dp) // 圆角大一点，更亲和
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    // 文字内容占据绝大部分空间
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = word.originalText,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 22.sp
                                        )

                                        if (word.correction.isNotEmpty()) {
                                            Spacer(Modifier.height(10.dp))
                                            // 用一个小标签标记纠错
                                            Text(
                                                text = "SUGGESTION",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFA000),
                                                modifier = Modifier
                                                    .background(Color(0xFFFFF8E1), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            Text(
                                                text = word.correction,
                                                fontSize = 15.sp,
                                                color = Color(0xFF5D4037),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }

                                    // 🚀 删除键：红色，但在右侧独立点击
                                    IconButton(
                                        onClick = { vm.toggleFavorite(word.originalText) },
                                        modifier = Modifier.size(32.dp).offset(x = 8.dp, y = (-8).dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // 底部的小灰字
                                Text(
                                    text = "# ${word.scene}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFBDBDBD),
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}