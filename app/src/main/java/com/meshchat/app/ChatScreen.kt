package com.meshchat.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.uiMessages.collectAsState()
    val peers by viewModel.activePeers.collectAsState()
    var textInput by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Chat (Offline)") },
                subtitle = { Text("Connected Peers: ${peers.size}") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }
            
            Divider()
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Trigger Image Picker */ }) {
                    Icon(Icons.Filled.Image, contentDescription = "Image")
                }
                
                IconButton(onClick = { 
                    isRecording = !isRecording
                    viewModel.toggleVoiceRecording() 
                }) {
                    Icon(
                        Icons.Filled.Mic, 
                        contentDescription = "Voice",
                        tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }

                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") }
                )

                IconButton(onClick = { 
                    viewModel.sendTextMessage(textInput)
                    textInput = "" 
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isMine = message.isMine
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bgColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer

    Column(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = alignment) {
        Text(text = message.senderName, style = MaterialTheme.typography.labelSmall)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isMine) Color.White else Color.Black
            )
        }
    }
}
