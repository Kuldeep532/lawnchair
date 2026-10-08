package app.lawnchair.tools

import android.os.Bundle
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.security.SecureRandom

class PasswordGeneratorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { PasswordGeneratorScreen(::finish) } }
    }
}

@Composable
private fun PasswordGeneratorScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var length by remember { mutableFloatStateOf(16f) }
    var letters by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }

    fun generate() {
        password = SecurePassword.generate(
            length = length.toInt(),
            letters = letters,
            numbers = numbers,
            symbols = symbols,
        )
    }

    LaunchedEffect(Unit) { generate() }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Create a strong password on your phone. Nothing is sent online.")

            Text("Length: " + length.toInt())
            Slider(
                value = length,
                onValueChange = { length = it },
                onValueChangeFinished = ::generate,
                valueRange = 8f..64f,
                steps = 55,
            )

            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = letters, onCheckedChange = { letters = it })
                Text("Letters", Modifier.padding(top = 12.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = numbers, onCheckedChange = { numbers = it })
                Text("Numbers", Modifier.padding(top = 12.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = symbols, onCheckedChange = { symbols = it })
                Text("Symbols", Modifier.padding(top = 12.dp))
            }

            Text(if (password.isEmpty()) "Choose at least one option." else password)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = ::generate,
                    enabled = letters || numbers || symbols,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("New Password")
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(password))
                    },
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("Copy")
                }
            }
        }
    }
}

private object SecurePassword {
    private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val NUMBERS = "23456789"
    private const val SYMBOLS = "!@#$%^&*_-+="

    fun generate(
        length: Int,
        letters: Boolean,
        numbers: Boolean,
        symbols: Boolean,
    ): String {
        val groups = buildList {
            if (letters) add(LETTERS)
            if (numbers) add(NUMBERS)
            if (symbols) add(SYMBOLS)
        }
        if (groups.isEmpty()) return ""
        val random = SecureRandom()
        val chars = StringBuilder(length)

        groups.forEach { group ->
            chars.append(group[random.nextInt(group.length)])
        }
        val all = groups.joinToString("")
        while (chars.length < length) {
            chars.append(all[random.nextInt(all.length)])
        }

        val result = chars.toString().toCharArray()
        for (index in result.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = result[index]
            result[index] = result[swapIndex]
            result[swapIndex] = value
        }
        return String(result)
    }
}
