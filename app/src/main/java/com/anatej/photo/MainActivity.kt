package com.anatej.photo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        incomingUri.value = intent?.let(::sharedImageUri)
        setContent {
            AnatejApp(pendingUri = incomingUri)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = sharedImageUri(intent)
    }

    private fun sharedImageUri(intent: Intent): Uri? {
        // Share-from-other-apps arrives as EXTRA_STREAM; SEND_MULTIPLE carries a list.
        val stream: Any? = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.firstOrNull()
            }
            else -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
        }
        return stream as? Uri
    }
}
