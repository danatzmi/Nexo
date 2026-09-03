package com.nexo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexo.app.data.repository.FirebaseBackendRepository
import com.nexo.app.ui.NexoRoot
import com.nexo.app.ui.theme.NexoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = FirebaseBackendRepository()

        setContent {
            NexoTheme {
                NexoRoot(repository = repository)
            }
        }
    }
}
