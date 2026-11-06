package com.meshchat.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

// We keep these definitions so the app compiles, even if we don't use them right now
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

// This is the DUMMY ViewModel. It ignores the database and bluetooth for this test.
class ChatViewModel(
    private val context: Context,
    val meshManager: NearbyMeshManager,
    private val mediaHandler: MediaPayloadHandler,
    private val chatDao: ChatDao
) : ViewModel() {

    private val _uiMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val uiMessages: StateFlow<List<ChatMessage>> = _uiMessages.asStateFlow()
    
    // Dummy peer list so the UI doesn't crash
    val activePeers = MutableStateFlow<List<String>>(emptyList())

    fun startMesh() {
        // Do nothing for this test
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        
        // Just add it to the list in memory. No DB, No Bluetooth.
        val newMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = "Me",
            messageType = "TEXT",
            content = text,
            timestamp = System.currentTimeMillis(),
            isMine = true
        )
        
        _uiMessages.value = _uiMessages.value + newMsg
    }

    fun sendImageMessage(uri: Uri) { /* Do nothing */ }
    fun toggleVoiceRecording() { /* Do nothing */ }

    override fun onCleared() {
        super.onCleared()
    }
}
