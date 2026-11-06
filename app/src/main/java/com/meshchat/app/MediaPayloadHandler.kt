package com.meshchat.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaPayloadHandler(private val context: Context) {

    private val CHUNK_SIZE = 16 * 1024 // 16KB MTU safe boundary
    private var mediaRecorder: MediaRecorder? = null
    private var currentVoiceFile: File? = null

    private val pendingImages = mutableMapOf<String, MutableMap<Int, ByteArray>>()
    private val pendingVoice = mutableMapOf<String, MutableMap<Int, ByteArray>>()

    suspend fun compressAndChunkImage(uri: Uri): Triple<String, List<ByteArray>, Int> = withContext(Dispatchers.IO) {
        val mediaId = java.util.UUID.randomUUID().toString()
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        
        val scaledBitmap = scaleBitmap(originalBitmap, 800)
        originalBitmap.recycle()

        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val compressedBytes = byteArrayOutputStream.toByteArray()
        scaledBitmap.recycle()

        val chunks = chunkData(compressedBytes)
        Triple(mediaId, chunks, chunks.size)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val height = bitmap.height
        val width = bitmap.width
        val ratio = maxDimension.toFloat() / maxOf(height, width).toFloat()
        
        if (ratio >= 1.0f) return bitmap
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun startVoiceRecording() {
        currentVoiceFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentVoiceFile?.absolutePath)
            prepare()
            start()
        }
    }

    suspend fun stopVoiceRecordingAndChunk(): Triple<String, List<ByteArray>, Int>? = withContext(Dispatchers.IO) {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null

        val file = currentVoiceFile ?: return@withContext null
        val mediaId = java.util.UUID.randomUUID().toString()
        val bytes = file.readBytes()
        file.delete()
        
        val chunks = chunkData(bytes)
        Triple(mediaId, chunks, chunks.size)
    }

    private fun chunkData(data: ByteArray): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + CHUNK_SIZE, data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    fun reassembleChunk(isImage: Boolean, mediaId: String, index: Int, total: Int, data: ByteArray): ByteArray? {
        val cache = if (isImage) pendingImages else pendingVoice
        
        val chunksMap = cache.getOrPut(mediaId) { mutableMapOf() }
        chunksMap[index] = data

        if (chunksMap.size == total) {
            val fullData = ByteArray(total * CHUNK_SIZE)
            var offset = 0
            for (i in 0 until total) {
                val chunk = chunksMap[i] ?: return null
                System.arraycopy(chunk, 0, fullData, offset, chunk.size)
                offset += chunk.size
            }
            cache.remove(mediaId)
            return fullData.copyOf(offset)
        }
        return null
    }
}
