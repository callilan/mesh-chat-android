package com.meshchat.app

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

@Serializable
enum class PayloadType {
    TEXT, IMAGE_METADATA, IMAGE_CHUNK, VOICE_METADATA, VOICE_CHUNK, ROUTING_ACK, PEER_PING
}

object ByteArrayAsStringSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteArray", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }
    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.getDecoder().decode(decoder.decodeString())
    }
}

@Serializable
data class MeshEnvelope(
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val ttl: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val payloadType: PayloadType,
    @Serializable(with = ByteArrayAsStringSerializer::class) val data: ByteArray,
    val chunkIndex: Int = -1,
    val totalChunks: Int = -1,
    val mediaId: String = "" 
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val messageType: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val messageType: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean
)
