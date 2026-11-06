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
    private var isRecording = false

    init {
        viewModelScope.launch {
            try {
                chatDao.getAllMessages().collect { entities ->
                    _uiMessages.value = entities.map { 
                        ChatMessage(it.id, it.senderName, it.messageType, it.content, it.timestamp, it.isMine) 
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Database error: ${e.message}")
            }
        }

        meshManager.onMessageReceived = { envelope, _ ->
            viewModelScope.launch { 
                try {
                    handleIncomingEnvelope(envelope) 
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Message handling error: ${e.message}")
                }
            }
        }
    }

    fun startMesh() {
        try {
            meshManager.startMeshNetwork()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Mesh start error: ${e.message}")
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
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
                meshManager.sendBroadcast(PayloadType.TEXT, text.toByteArray())
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Send message error: ${e.message}")
            }
        }
    }

    fun sendImageMessage(uri: Uri) {
        viewModelScope.launch {
            try {
                val (mediaId, chunks, total) = mediaHandler.compressAndChunkImage(uri)
                
                meshManager.sendBroadcast(PayloadType.IMAGE_METADATA, mediaId.toByteArray(), mediaId)
                
                chunks.forEachIndexed { index, chunk ->
                    meshManager.sendBroadcast(PayloadType.IMAGE_CHUNK, chunk, mediaId, index, total)
                }
                
                val dbMsg = ChatMessageEntity(UUID.randomUUID().toString(), meshManager.localNodeId, "Me", "IMAGE", "Received Image", System.currentTimeMillis(), true)
                chatDao.insertMessage(dbMsg)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Image send error: ${e.message}")
            }
        }
    }

    fun toggleVoiceRecording() {
        if (isRecording) {
            isRecording = false
            viewModelScope.launch {
                try {
                    val result = mediaHandler.stopVoiceRecordingAndChunk() ?: return@launch
                    val (mediaId, chunks, total) = result
                    
                    meshManager.sendBroadcast(PayloadType.VOICE_METADATA, mediaId.toByteArray(), mediaId)
                    chunks.forEachIndexed { index, chunk ->
                        meshManager.sendBroadcast(PayloadType.VOICE_CHUNK, chunk, mediaId, index, total)
                    }
                    
                    val dbMsg = ChatMessageEntity(UUID.randomUUID().toString(), meshManager.localNodeId, "Me", "VOICE", "Received Voice", System.currentTimeMillis(), true)
                    chatDao.insertMessage(dbMsg)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Voice recording error: ${e.message}")
                }
            }
        } else {
            isRecording = true
            mediaHandler.startVoiceRecording()
        }
    }

    private suspend fun handleIncomingEnvelope(envelope: MeshEnvelope) {
        if (envelope.senderId == meshManager.localNodeId) return

        try {
            when (envelope.payloadType) {
                PayloadType.TEXT -> {
                    val text = String(envelope.data)
                    val dbMsg = ChatMessageEntity(envelope.messageId, envelope.senderId, "Peer", "TEXT", text, envelope.timestamp, false)
                    chatDao.insertMessage(dbMsg)
                }
                PayloadType.IMAGE_CHUNK -> {
                    val fullImage = mediaHandler.reassembleChunk(true, envelope.mediaId, envelope.chunkIndex, envelope.totalChunks, envelope.data)
                    if (fullImage != null) {
                        val dbMsg = ChatMessageEntity(envelope.mediaId, envelope.senderId, "Peer", "IMAGE", "Received Image", envelope.timestamp, false)
                        chatDao.insertMessage(dbMsg)
                    }
                }
                PayloadType.VOICE_CHUNK -> {
                    val fullAudio = mediaHandler.reassembleChunk(false, envelope.mediaId, envelope.chunkIndex, envelope.totalChunks, envelope.data)
                    if (fullAudio != null) {
                        val dbMsg = ChatMessageEntity(envelope.mediaId, envelope.senderId, "Peer", "VOICE", "Received Voice", envelope.timestamp, false)
                        chatDao.insertMessage(dbMsg)
                    }
                }
                else -> { /* Handle Metadata, ACKs, Pings */ }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Incoming message error: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        meshManager.stopMeshNetwork()
    }
}
