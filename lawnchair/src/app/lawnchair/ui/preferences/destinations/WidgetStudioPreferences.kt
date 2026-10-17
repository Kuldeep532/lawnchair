package app.lawnchair.ui.preferences.destinations

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.widgets.custom.NexusAppWidgetProvider

private data class WidgetApp(val label: String, val packageName: String, val className: String)

private val widgetColors = listOf(
    "Nexus Blue" to 0xFF315DA8.toInt(),
    "Emerald" to 0xFF167D5A.toInt(),
    "Violet" to 0xFF7650A8.toInt(),
    "Sunset" to 0xFFB85B36.toInt(),
    "Graphite" to 0xFF343943.toInt(),
    "Rose" to 0xFFAD466C.toInt(),
)

@Composable
fun WidgetStudioPreferences() {
    val context = LocalContext.current
    val apps = remember { loadWidgetApps(context) }
    var selectedPackage by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(widgetColors.first().second) }

    Scaffold(topBar = { TopAppBar(title = { Text("Widget Studio") }) }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Create a home-screen app widget. Choose an app and a colour; it opens the app and shows app-published shortcuts when available.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Choose an app", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedPackage = app.packageName
                            selectedClass = app.className
                            selectedLabel = app.label
                        }.padding(vertical = 10.dp, horizontal = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selectedPackage == app.packageName) {
                            Text("Selected", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Text("Widget colour", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                widgetColors.take(3).forEach { (name, color) ->
                    FilterChip(
                        selected = selectedColor == color,
                        onClick = { selectedColor = color },
                        label = { Text(name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                widgetColors.drop(3).forEach { (name, color) ->
                    FilterChip(
                        selected = selectedColor == color,
                        onClick = { selectedColor = color },
                        label = { Text(name) },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPackage.isNotBlank(),
                onClick = {
                    context.getSharedPreferences(NexusAppWidgetProvider.PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(NexusAppWidgetProvider.KEY_PACKAGE, selectedPackage)
                        .putString(NexusAppWidgetProvider.KEY_CLASS, selectedClass)
                        .putString(NexusAppWidgetProvider.KEY_LABEL, selectedLabel)
                        .putInt(NexusAppWidgetProvider.KEY_COLOR, selectedColor)
                        .apply()
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, NexusAppWidgetProvider::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        manager.isRequestPinAppWidgetSupported
                    ) {
                        val accepted = manager.requestPinAppWidget(provider, null, null)
                        if (!accepted) Toast.makeText(context, "Android could not add the widget. Try adding Nexus App Widget from the home-screen widget picker.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Open the home-screen widget picker and add Nexus App Widget.", Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                Text("Create Widget")
            }
        }
    }
}

private fun loadWidgetApps(context: Context): List<WidgetApp> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return runCatching {
        launcherApps.getActivityList(null, Process.myUserHandle())
            .map { info ->
                WidgetApp(
                    label = info.label?.toString().orEmpty().ifBlank { info.applicationInfo.packageName },
                    packageName = info.applicationInfo.packageName,
                    className = info.componentName.className,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}
