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
    val activePeers = MutableStateFlow<List<String>>(emptyList()) // Dummy list for Safe Mode

    init {
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
    }

    fun startMesh() {
        // Bluetooth disabled for Safe Mode
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val dbMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    senderId = "Me",
                    senderName = "Me",
                    messageType = "TEXT",
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    isMine = true
                )
                chatDao.insertMessage(dbMsg)
                // meshManager.sendBroadcast(...) // DISABLED FOR SAFE MODE
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Send Error: ${e.message}")
            }
        }
    }

    fun sendImageMessage(uri: Uri) { }
    fun toggleVoiceRecording() { }
}
