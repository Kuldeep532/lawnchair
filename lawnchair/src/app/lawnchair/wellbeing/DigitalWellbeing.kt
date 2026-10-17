package app.lawnchair.wellbeing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar

private const val PREFS = "nexus_wellbeing"
private const val KEY_DAILY_LIMIT = "daily_limit_minutes"
private const val KEY_SOCIAL_BLOCK_MINUTES = "social_block_minutes"
private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
private val DEFAULT_BLOCKED = setOf(
    "com.instagram.android",
    "com.facebook.katana",
)

private val ALLOWED = setOf(
    "com.phonepe.app",
    "com.google.android.apps.nbu.paisa.user",
    "com.google.android.youtube",
)

class DigitalWellbeingController(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dailyLimitMinutes(): Int = prefs.getInt(KEY_DAILY_LIMIT, 180)
    fun setDailyLimitMinutes(value: Int) = prefs.edit().putInt(KEY_DAILY_LIMIT, value.coerceIn(15, 720)).apply()

    fun socialBlockMinutes(): Int = prefs.getInt(KEY_SOCIAL_BLOCK_MINUTES, 30)
    fun setSocialBlockMinutes(value: Int) = prefs.edit().putInt(KEY_SOCIAL_BLOCK_MINUTES, value.coerceIn(5, 180)).apply()

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun isAllowedApp(packageName: String): Boolean =
        packageName in ALLOWED || packageName == context.packageName

    fun blockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, DEFAULT_BLOCKED)?.toSet() ?: DEFAULT_BLOCKED

    fun setBlockedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages.toSet()).apply()
    }

    fun isSocialApp(packageName: String): Boolean =
        packageName in blockedPackages() && !isAllowedApp(packageName)

    fun foregroundPackage(): String? {
        if (!isUsageAccessGranted()) return null
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - 15_000L
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        var lastTimestamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.timeStamp >= lastTimestamp
            ) {
                lastTimestamp = event.timeStamp
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    fun startOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun packageUsageToday(packageName: String): Long {
        if (!isUsageAccessGranted()) return 0L
        val manager = context.getSystemService(UsageStatsManager::class.java)
        return manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDayMillis(),
            System.currentTimeMillis(),
        ).firstOrNull { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }

    fun totalBlockedUsageToday(): Long {
        if (!isUsageAccessGranted()) return 0L
        val blocked = blockedPackages()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        return manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDayMillis(),
            System.currentTimeMillis(),
        ).filter { it.packageName in blocked && !isAllowedApp(it.packageName) }
            .sumOf { it.totalTimeInForeground }
    }

    fun shouldBlockPackage(packageName: String): Boolean {
        if (!isUsageAccessGranted() || !isSocialApp(packageName)) return false
        val appMinutes = packageUsageToday(packageName) / 60_000L
        val totalMinutes = totalBlockedUsageToday() / 60_000L
        return appMinutes >= socialBlockMinutes().toLong() ||
            totalMinutes >= dailyLimitMinutes().toLong()
    }
}

@Composable
fun DigitalWellbeingSettings() {
    val context = LocalContext.current
    val controller = remember { DigitalWellbeingController(context) }
    var dailyLimit by remember { mutableIntStateOf(controller.dailyLimitMinutes()) }
    var socialBlock by remember { mutableIntStateOf(controller.socialBlockMinutes()) }
    var access by remember { mutableStateOf(controller.isUsageAccessGranted()) }
    var blockedPackages by remember { mutableStateOf(controller.blockedPackages()) }
    val installedApps = remember {
        runCatching {
            context.getSystemService(LauncherApps::class.java)
                .getActivityList(null, Process.myUserHandle())
                .map { it.applicationInfo.packageName to (it.label?.toString() ?: it.applicationInfo.packageName) }
                .distinctBy { it.first }
                .filter { it.first != context.packageName && !controller.isAllowedApp(it.first) }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        while (true) {
            access = controller.isUsageAccessGranted()
            delay(1000)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Digital Wellbeing") }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose distracting apps and set daily usage limits. PhonePe, Google Pay and YouTube remain excluded.")
            Text("Daily total limit: $dailyLimit minutes", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = dailyLimit.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let {
                        dailyLimit = it.coerceIn(15, 720)
                        controller.setDailyLimitMinutes(dailyLimit)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Daily total limit (minutes)") },
                singleLine = true,
            )
            Text("Per-app limit: $socialBlock minutes", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = socialBlock == minutes,
                        onClick = {
                            socialBlock = minutes
                            controller.setSocialBlockMinutes(minutes)
                        },
                        label = { Text("$minutes") },
                    )
                }
            }
            Text(
                if (access) "Usage access is enabled." else "Enable usage access to measure app time and enforce limits.",
            )
            Button(
                onClick = { controller.openUsageAccessSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (access) "Review usage access" else "Enable usage access")
            }
            Text("Apps to limit", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(installedApps, key = { it.first }) { (packageName, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = packageName in blockedPackages,
                            onCheckedChange = { checked ->
                                blockedPackages = if (checked) blockedPackages + packageName else blockedPackages - packageName
                                controller.setBlockedPackages(blockedPackages)
                            },
                        )
                        Column(Modifier.weight(1f).padding(top = 10.dp)) {
                            Text(label)
                            Text(
                                if (access) "Used today: ${controller.packageUsageToday(packageName) / 60_000L} min"
                                else packageName,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Text(
                "Limits rely on Android Usage Access and launcher monitoring. Android may restrict background enforcement; this is not a device-wide parental-control lock.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

suspend fun runWellbeingTick(context: Context) {
    val controller = DigitalWellbeingController(context)
    if (!controller.isUsageAccessGranted()) return
    var lastBlockedPackage: String? = null
    repeat(2) {
        delay(750)
        val foreground = controller.foregroundPackage()
        if (foreground == null || foreground == context.packageName) return@repeat
        if (!controller.isSocialApp(foreground)) return@repeat
        val used = controller.packageUsageToday(foreground) / 60_000L
        if ((used >= controller.socialBlockMinutes() || controller.totalBlockedUsageToday() / 60_000L >= controller.dailyLimitMinutes()) && foreground != lastBlockedPackage) {
            lastBlockedPackage = foreground
            context.startActivity(
                Intent(context, DigitalWellbeingBlockedActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}

class DigitalWellbeingBlockedActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.activity.compose.setContent {
            app.lawnchair.ui.theme.LawnchairTheme {
                EdgeToEdge()
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Focus Mode") }) },
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("This app is temporarily blocked to help you stay within your time limit.")
                        Button(
                            onClick = {
                                startActivity(Intent(this@DigitalWellbeingBlockedActivity, app.lawnchair.LawnchairLauncher::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Back to Launcher") }
                    }
                }
            }
        }
    }
}