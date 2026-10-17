package app.lawnchair.wellbeing

import android.app.AppOpsManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import kotlinx.coroutines.delay
import java.util.Calendar

private const val PREFS = "nexus_wellbeing"
private const val KEY_DAILY_LIMIT = "daily_limit_minutes"
private const val KEY_SOCIAL_BLOCK_MINUTES = "social_block_minutes"
private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
private const val KEY_FOCUS_UNTIL = "focus_until"
private const val KEY_FOCUS_PACKAGES = "focus_packages"
private val DEFAULT_BLOCKED = setOf(
    "com.instagram.android",
    "com.facebook.katana",
)

private val ALLOWED = setOf(
    "com.phonepe.app",
    "com.google.android.apps.nbu.paisa.user",
    "net.one97.paytm",
    "in.org.npci.upiapp",
    "com.google.android.dialer",
    "com.android.dialer",
    "com.android.phone",
    "com.google.android.apps.messaging",
    "com.android.messaging",
    "com.whatsapp",
    "org.telegram.messenger",
    "com.android.settings",
    "com.android.systemui",
    "com.google.android.apps.maps",
    "com.android.camera2",
    "com.google.android.deskclock",
    "com.google.android.contacts",
    "com.android.contacts",
    "com.android.vending",
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

    fun isAccessibilityMonitoringEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == "app.lawnchair.LawnchairAccessibilityService" }
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

    fun focusUntilMillis(): Long = prefs.getLong(KEY_FOCUS_UNTIL, 0L)
    fun isFocusSessionActive(): Boolean = System.currentTimeMillis() < focusUntilMillis()

    fun focusPackages(): Set<String> =
        prefs.getStringSet(KEY_FOCUS_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun startFocusSession(packages: Set<String>, durationMillis: Long = 24L * 60L * 60L * 1000L) {
        val eligible = packages.filterNot { isAllowedApp(it) }.toSet()
        prefs.edit()
            .putStringSet(KEY_FOCUS_PACKAGES, eligible)
            .putLong(KEY_FOCUS_UNTIL, System.currentTimeMillis() + durationMillis)
            .apply()
    }

    fun endFocusSession() {
        prefs.edit().putLong(KEY_FOCUS_UNTIL, 0L).putStringSet(KEY_FOCUS_PACKAGES, emptySet()).apply()
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
        if (isAllowedApp(packageName)) return false
        if (isFocusSessionActive() && packageName in focusPackages()) return true
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
    var monitoring by remember { mutableStateOf(controller.isAccessibilityMonitoringEnabled()) }
    var blockedPackages by remember { mutableStateOf(controller.blockedPackages()) }
    var focusUntil by remember { mutableStateOf(controller.focusUntilMillis()) }
    var showStartConsent by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var emergencyPhrase by remember { mutableStateOf("") }
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
            monitoring = controller.isAccessibilityMonitoringEnabled()
            focusUntil = controller.focusUntilMillis()
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
            Text("Choose distracting apps and set daily usage limits. Phone, messaging, payment and core utility apps are protected. YouTube can be selected if you want to limit it.")
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
            if (focusUntil > System.currentTimeMillis()) {
                val remainingMinutes = ((focusUntil - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
                Text("24-hour Focus Session active · about ${remainingMinutes / 60}h ${remainingMinutes % 60}m remaining")
                Button(onClick = { showEmergencyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Emergency override")
                }
            } else {
                Button(
                    onClick = { showStartConsent = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = blockedPackages.isNotEmpty() && monitoring,
                ) { Text("Start 24-hour Focus Session") }
                Text("Choose distracting apps below first. A focus session blocks only those apps for 24 hours; essential phone, messaging, payment and system apps are excluded.")
            }
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
                            enabled = focusUntil <= System.currentTimeMillis(),
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
                item {
                    Text("Learn instead of endlessly scrolling", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("Pick one small goal: learn a skill, practise English, complete a course, or explore legitimate work opportunities.")
                    Button(onClick = { openWellbeingResource(context, "https://www.duolingo.com/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Practise English · Duolingo")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://www.youtube.com/@bbclearningenglish") }, modifier = Modifier.fillMaxWidth()) {
                        Text("English lessons · BBC Learning English")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://www.khanacademy.org/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Study maths and more · Khan Academy")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://swayam.gov.in/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Free courses · SWAYAM")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://nptel.ac.in/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Technical courses · NPTEL")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://www.skillindiadigital.gov.in/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Job skills · Skill India Digital Hub")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://internshala.com/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Explore internships · Internshala")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://www.ncs.gov.in/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Explore jobs · National Career Service")
                    }
                    Button(onClick = { openWellbeingResource(context, "https://www.fiverr.com/") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Explore freelance work · Fiverr")
                    }
                    Text(
                        "Safety tip: courses and job listings do not guarantee income. Never pay a recruiter to get a job, and never share OTPs, UPI PINs, or banking passwords.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "For live app-switch monitoring, enable Nexus Launcher in Android Accessibility settings too.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Text(
                if (monitoring) "Accessibility monitoring is enabled."
                else "Enable Accessibility monitoring before starting a Focus Session.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (monitoring) "Review accessibility monitoring" else "Enable accessibility monitoring")
            }
            Text(
                "Transparency: Focus Mode blocks selected apps only. It does not prevent uninstalling Nexus Launcher or changing Android permissions; Android reserves device-owner controls for managed devices.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showStartConsent) {
        AlertDialog(
            onDismissRequest = { showStartConsent = false },
            title = { Text("Start a 24-hour Focus Session?") },
            text = {
                Text("You are choosing to block ${blockedPackages.size} selected apps for the next 24 hours. Phone, messages, payment and core utility apps are excluded. You can end early only through the visible emergency override. Continue?")
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.startFocusSession(blockedPackages)
                    focusUntil = controller.focusUntilMillis()
                    showStartConsent = false
                }) { Text("I agree — start") }
            },
            dismissButton = {
                TextButton(onClick = { showStartConsent = false }) { Text("Cancel") }
            },
        )
    }

    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false; emergencyPhrase = "" },
            title = { Text("Emergency override") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only use this if an urgent situation requires access. Ending the focus session will immediately allow selected apps again.")
                    OutlinedTextField(
                        value = emergencyPhrase,
                        onValueChange = { emergencyPhrase = it },
                        label = { Text("Type END FOCUS to confirm") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = emergencyPhrase == "END FOCUS",
                    onClick = {
                        controller.endFocusSession()
                        focusUntil = 0L
                        showEmergencyDialog = false
                        emergencyPhrase = ""
                    },
                ) { Text("End focus session") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false; emergencyPhrase = "" }) { Text("Keep focus on") }
            },
        )
    }
}

suspend fun runWellbeingTick(context: Context) {
    val controller = DigitalWellbeingController(context)
    var lastBlockedPackage: String? = null
    repeat(2) {
        delay(750)
        val foreground = controller.foregroundPackage()
        if (foreground == null || foreground == context.packageName) return@repeat
        if (!controller.shouldBlockPackage(foreground)) return@repeat
        if (foreground != lastBlockedPackage) {
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
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@DigitalWellbeingBlockedActivity, app.lawnchair.LawnchairLauncher::class.java))
                finish()
            }
        })
        EdgeToEdge()
        setContent {
            app.lawnchair.ui.theme.LawnchairTheme {
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

private fun openWellbeingResource(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "Could not open this link. Please check your browser.", Toast.LENGTH_LONG).show()
    }
}
