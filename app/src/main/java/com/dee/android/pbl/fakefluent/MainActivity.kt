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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

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
        tts?.stop(); tts?.shutdown()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Practice Scenarios", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(vm.scenarios) { scenario ->
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(scenario.icon, modifier = Modifier.padding(end = 12.dp), fontSize = 20.sp)
                                    Text(scenario.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { vm.deleteScenario(scenario) }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    inputText = scenario.prompt // 🚀 填入输入框
                                }
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
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                    title = { Text("FakeFluent", fontWeight = FontWeight.Black) },
                    actions = { IconButton(onClick = { vm.isSheetOpen = true }) { Icon(Icons.Default.Settings, "Settings") } }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = 16.dp)) {
                    items(vm.chatMessages) { ChatBubble(it) { if (!it.isUser) vm.speakText(it.content) } }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()) {
                    TextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("Enter message or use scenario...") },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { if (inputText.isNotBlank()) { vm.sendMessage(inputText); inputText = "" } },
                        containerColor = Color(0xFF2196F3), shape = CircleShape, modifier = Modifier.size(52.dp)
                    ) { Icon(if (vm.isLoading) Icons.Default.Refresh else Icons.AutoMirrored.Filled.Send, "Action", tint = Color.White) }
                }
            }
        }
    }

    // --- 弹窗：新增场景 ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Scenario") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newIcon, onValueChange = { if (it.length <= 2) newIcon = it }, label = { Text("Emoji") }, modifier = Modifier.width(80.dp))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newPrompt, onValueChange = { newPrompt = it }, label = { Text("AI Prompt") }, modifier = Modifier.height(120.dp).fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addScenario(newTitle, newPrompt, newIcon)
                    showAddDialog = false
                    newTitle = ""; newPrompt = ""; newIcon = "✨"
                }) { Text("Save") }
            }
        )
    }

    if (vm.isSheetOpen) ModalBottomSheet(onDismissRequest = { vm.isSheetOpen = false }) { SettingsContent(vm) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(vm: ChatViewModel) {
    var showKeyDialog by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf("") }
    val providers = listOf("SiliconFlow (Qwen)" to "Qwen/Qwen2.5-7B-Instruct", "SiliconFlow (DeepSeek)" to "deepseek-ai/DeepSeek-V3", "Groq (国外)" to "llama-3.3-70b-versatile", "Gemini (国外)" to "gemini-1.5-flash")

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

@Composable
fun ChatBubble(message: ChatMessageUI, onSpeak: () -> Unit) {
    val isUser = message.isUser
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            val parts = message.content.split("Correction:")
            Surface(
                color = if (isUser) Color(0xFF007AFF) else Color.White,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 280.dp).clickable(enabled = !isUser) { onSpeak() }
            ) {
                Text(text = parts[0].trim(), modifier = Modifier.padding(12.dp), color = if (isUser) Color.White else Color.Black)
            }
            if (parts.size > 1 && !isUser) {
                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp).widthIn(max = 260.dp)) {
                    Text(text = "Teacher's Note: " + parts[1].trim(), modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = Color(0xFFE65100))
                }
            }
        }
    }
}