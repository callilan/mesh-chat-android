package com.meshchat.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.Room

class MainActivity : ComponentActivity() {

    // FIX: Added 'by mutableStateOf' so the screen updates instantly when this changes!
    private var errorMessage by mutableStateOf<String?>(null)
    private var viewModel by mutableStateOf<ChatViewModel?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            initViewModelAndStart()
        } else {
            errorMessage = "Permissions denied. Please enable Bluetooth and Location in settings."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // We call setContent FIRST now to prevent the hang
        setContent {
            // DARK THEME ENABLED HERE
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (errorMessage != null) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = "APP ERROR:\n\n$errorMessage", 
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else if (viewModel != null) {
                        ChatScreen(viewModel!!)
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(text = "Initializing Mesh Network...", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        
        // Now we ask for permissions
        requestPermissions()
    }

    private fun initViewModelAndStart() {
        try {
            val db = Room.databaseBuilder(
                applicationContext,
                ChatDatabase::class.java,
                "chat-db"
            )
            .fallbackToDestructiveMigration()
            .build()

            val vm = ChatViewModel(
                applicationContext,
                NearbyMeshManager(applicationContext),
                MediaPayloadHandler(applicationContext),
                db.chatDao()
            )
            viewModel = vm
            // vm.startMesh() // Still disabled for stability
            
        } catch (e: Throwable) {
            errorMessage = "Failed to initialize app: ${e.message}"
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        } else {
            initViewModelAndStart()
        }
    }
}
