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
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) { ChatScreen(tts = tts) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel(), tts: TextToSpeech?) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(tts) { tts?.let { chatViewModel.setTTS(it) } }
    LaunchedEffect(chatViewModel.chatMessages.size) {
        if (chatViewModel.chatMessages.isNotEmpty()) listState.animateScrollToItem(chatViewModel.chatMessages.size - 1)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("FakeFluent Coach", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { chatViewModel.isSheetOpen = true }) { Icon(Icons.Default.Settings, null) } }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                items(chatViewModel.chatMessages) { message -> ChatBubble(message) }
            }
            // 修正后的 Row
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                TextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Speak English...") }, shape = RoundedCornerShape(24.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(onClick = { if (inputText.isNotBlank()) { chatViewModel.sendMessage(inputText); inputText = "" } }, containerColor = Color(0xFF2196F3), shape = CircleShape) {
                    if (chatViewModel.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                }
            }
        }

        if (chatViewModel.isSheetOpen) {
            ModalBottomSheet(onDismissRequest = { chatViewModel.isSheetOpen = false }, sheetState = sheetState) {
                Column(Modifier.padding(24.dp).fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("API Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Provider", color = Color.Gray)
                    Row {
                        ProviderRadio("SiliconFlow (直连)", "SiliconFlow", chatViewModel)
                        Spacer(Modifier.width(10.dp))
                        ProviderRadio("Groq (需VPN)", "Groq", chatViewModel)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Model", color = Color.Gray)
                    if (chatViewModel.currentProvider == "SiliconFlow") {
                        ModelRadio("DeepSeek-V3", "deepseek-ai/DeepSeek-V3", chatViewModel)
                    } else {
                        ModelRadio("Llama-3.3-70B", "llama-3.3-70b-versatile", chatViewModel)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { chatViewModel.clearHistory(); chatViewModel.isSheetOpen = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Clear History") }
                }
            }
        }
    }
}

@Composable
fun ProviderRadio(label: String, id: String, vm: ChatViewModel) {
    // 修正后的 Row
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
        vm.currentProvider = id
        vm.currentModel = if(id == "SiliconFlow") "deepseek-ai/DeepSeek-V3" else "llama-3.3-70b-versatile"
    }) {
        RadioButton(selected = vm.currentProvider == id, onClick = {
            vm.currentProvider = id
            vm.currentModel = if(id == "SiliconFlow") "deepseek-ai/DeepSeek-V3" else "llama-3.3-70b-versatile"
        })
        Text(label, fontSize = 14.sp)
    }
}

@Composable
fun ModelRadio(label: String, id: String, vm: ChatViewModel) {
    // 修正后的 Row
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.currentModel = id }) {
        RadioButton(selected = vm.currentModel == id, onClick = { vm.currentModel = id })
        Text(label)
    }
}

@Composable
fun ChatBubble(message: ChatMessageUI) {
    val isUser = message.isUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = if (isUser) Color(0xFF007AFF) else Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 280.dp)) {
            Text(message.content, Modifier.padding(12.dp), color = if (isUser) Color.White else Color.Black)
        }
    }
}