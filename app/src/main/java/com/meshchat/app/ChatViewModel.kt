package com.meshchat.app

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)
}

@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}

class ChatViewModel(
    private val context: Context,
    val meshManager: NearbyMeshManager,
    private val mediaHandler: MediaPayloadHandler,
    private val chatDao: ChatDao
) : ViewModel() {

    private val _uiMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val uiMessages: StateFlow<List<ChatMessage>> = _uiMessages.asStateFlow()
    val activePeers = meshManager.activePeersFlow

    init {
        // 1. Load existing messages from Database
        viewModelScope.launch {
            try {
                chatDao.getAllMessages().collect { entities ->
                    _uiMessages.value = entities.map { 
                        ChatMessage(it.id, it.senderName, it.messageType, it.content, it.timestamp, it.isMine) 
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "DB Error: ${e.message}")
            }
        }

        // 2. Listen for incoming Bluetooth messages
        meshManager.onMessageReceived = { envelope, _ ->
            viewModelScope.launch { 
                try {
                    if (envelope.payloadType == PayloadType.TEXT) {
                        val text = String(envelope.data)
                        val dbMsg = ChatMessageEntity(
                            id = envelope.messageId,
                            senderId = envelope.senderId,
                            senderName = "Peer",
                            messageType = "TEXT",
                            content = text,
                            timestamp = envelope.timestamp,
                            isMine = false
                        )
                        chatDao.insertMessage(dbMsg)
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Receive Error: ${e.message}")
                }
            }
        }
    }

    fun startMesh() {
        try {
            meshManager.startMeshNetwork()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Mesh Start Error: ${e.message}")
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                // 1. Save to local database first
                val dbMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    senderId = meshManager.localNodeId,
                    senderName = "Me",
                    messageType = "TEXT",
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    isMine = true
                )
                chatDao.insertMessage(dbMsg)
                
                // 2. Broadcast over Bluetooth
                meshManager.sendBroadcast(PayloadType.TEXT, text.toByteArray())
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Send Error: ${e.message}")
            }
        }
    }

    fun sendImageMessage(uri: Uri) { /* Placeholder for future */ }
    fun toggleVoiceRecording() { /* Placeholder for future */ }

    override fun onCleared() {
        super.onCleared()
        try { meshManager.stopMeshNetwork() } catch (e: Exception) {}
    }
}
