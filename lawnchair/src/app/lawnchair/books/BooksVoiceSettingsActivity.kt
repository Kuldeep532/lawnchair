package app.lawnchair.books

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

class BooksVoiceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent {
            LawnchairTheme {
                var selected by remember { mutableStateOf("Phone default TTS") }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            title = { Text("TTS & Voice") },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Voice downloads and voice selection stay outside the reader.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = { selected = "Phone default TTS" }) {
                            Text("Use phone default TTS")
                        }
                        Button(onClick = { selected = "Downloaded voice" }) {
                            Text("Choose downloaded voice")
                        }
                        Text("Selected: $selected")
                    }
                }
            }
        }
    }
}
