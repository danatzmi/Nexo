package com.nexo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nexo.app.data.repository.FirebaseBackendRepository
import com.nexo.app.domain.model.DeepLink
import com.nexo.app.domain.model.DeepLinkParser
import com.nexo.app.ui.NexoRoot
import com.nexo.app.ui.theme.NexoTheme

class MainActivity : ComponentActivity() {

    private var pendingJoinCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = FirebaseBackendRepository()
        pendingJoinCode = extractJoinCode(intent)

        setContent {
            NexoTheme {
                NexoRoot(
                    repository = repository,
                    pendingJoinCode = pendingJoinCode,
                    onConsumedPendingJoinCode = { pendingJoinCode = null }
                )
            }
        }
    }

    /** `launchMode="singleTop"` routes a warm-start deep link tap here instead of creating a new Activity instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractJoinCode(intent)?.let { pendingJoinCode = it }
    }

    private fun extractJoinCode(intent: Intent): String? {
        val data = intent.dataString ?: return null
        return (DeepLinkParser.parse(data) as? DeepLink.JoinGym)?.code
    }
}
