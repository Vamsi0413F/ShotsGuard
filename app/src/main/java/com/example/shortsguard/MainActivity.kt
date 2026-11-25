package com.example.shortsguard

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        prefs.pruneOld()
        Status.createChannels(this)
        AlertWorker.schedule(this)

        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var onboarded by remember { mutableStateOf(prefs.onboarded) }
                    if (onboarded) {
                        Screen(prefs, onReplaySetup = {
                            prefs.onboarded = false
                            onboarded = false
                        })
                    } else {
                        Onboarding(prefs, onDone = {
                            prefs.onboarded = true
                            onboarded = true
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// =====================================================================================
// First-run walkthrough
// =====================================================================================

@Composable
fun Onboarding(prefs: Prefs, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var serviceOn by remember { mutableStateOf(Util.isServiceEnabled(context)) }
    var notifOn by remember { mutableStateOf(Util.notificationsGranted(context)) }
    var batteryOn by remember { mutableStateOf(Util.isIgnoringBattery(context)) }

    // Re-check while the user is away in Android settings, so ticks appear when they come back.
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = Util.isServiceEnabled(context)
            notifOn = Util.notificationsGranted(context)
            batteryOn = Util.isIgnoringBattery(context)
            delay(700)
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = Util.notificationsGranted(context)
    }

    val lastStep = 4
    val stepDone = when (step) {
        1 -> serviceOn
        2 -> notifOn
        3 -> batteryOn
        else -> true
    }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Setup ${step + 1} of ${lastStep + 1}", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { (step + 1) / (lastStep + 1f) },
            modifier = Modifier.fillMaxWidth(),
        )

        when (step) {
            0 -> {
                Text("Welcome to ShortsGuard", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "ShortsGuard blocks Instagram Reels and YouTube Shorts while leaving the rest of " +
                        "those apps alone. You can block them completely or give yourself a daily allowance " +
                        "that resets automatically.",
                )
                Text("Setup takes about a minute and has three short steps.")
            }

            1 -> {
                Text("Turn on the accessibility service", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Android needs your permission before ShortsGuard can notice when Reels or Shorts is " +
                        "open. It only looks at the screen layout of Instagram and YouTube. It does not read, " +
                        "store or send your messages, passwords or videos.",
                )
                Text("Tap the button, find ShortsGuard under Installed apps (or Downloaded apps), and switch it on.")
                Button(onClick = { Util.openAccessibilitySettings(context) }) {
                    Text("Open Accessibility settings")
                }
                Text(
                    "If the switch is greyed out: open App info, tap the three-dot menu at the top right, " +
                        "choose \"Allow restricted settings\", then try again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { Util.openAppInfo(context) }) { Text("Open App info") }
                DoneLine(serviceOn, "Accessibility service is on", "Not on yet")
            }

            2 -> {
                Text("Allow notifications", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "ShortsGuard shows how many minutes you have left today, and warns you if the " +
                        "blocker ever gets switched off.",
                )
                if (Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                } else {
                    Text("Nothing to do on this version of Android.")
                }
                DoneLine(notifOn, "Notifications allowed", "Not allowed yet")
            }

            3 -> {
                Text("Keep it running in the background", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Many phones (Xiaomi, Oppo, Vivo, Samsung and others) stop background apps to save " +
                        "battery, which would silently switch the blocker off. Set ShortsGuard to " +
                        "unrestricted battery use.",
                )
                Button(onClick = { Util.requestBatteryExemption(context) }) {
                    Text("Set battery to unrestricted")
                }
                Text(
                    "On some phones you also need to allow Autostart or \"Background activity\" in App info.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { Util.openAppInfo(context) }) { Text("Open App info") }
                DoneLine(batteryOn, "Battery use is unrestricted", "Not set yet")
            }

            else -> {
                Text("You're set", style = MaterialTheme.typography.headlineMedium)
                DoneLine(serviceOn, "Accessibility service on", "Accessibility service is still off")
                DoneLine(notifOn, "Notifications allowed", "Notifications not allowed")
                DoneLine(batteryOn, "Battery unrestricted", "Battery not unrestricted")
                Text(
                    "Reels and Shorts start out fully blocked. On the next screen, use the Daily allowance " +
                        "slider if you want a few minutes per day. You can add the home-screen widget by " +
                        "long-pressing your home screen and choosing Widgets > ShortsGuard.",
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step == lastStep) onDone() else step += 1 }) {
                Text(
                    when {
                        step == lastStep -> "Finish"
                        stepDone -> "Next"
                        else -> "Skip"
                    },
                )
            }
        }
    }
}

@Composable
private fun DoneLine(ok: Boolean, okText: String, notText: String) {
    Text(
        if (ok) "✓ $okText" else "✗ $notText",
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.titleSmall,
    )
}

// =====================================================================================
// Main screen
// =====================================================================================

@Composable
fun Screen(prefs: Prefs, onReplaySetup: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var s by remember { mutableStateOf(prefs.snapshot()) }
    var serviceOn by remember { mutableStateOf(Util.isServiceEnabled(context)) }
    var notifOn by remember { mutableStateOf(Util.notificationsGranted(context)) }
    var batteryOn by remember { mutableStateOf(Util.isIgnoringBattery(context)) }

    // Refresh once a second so usage numbers and the setup checklist stay live.
    LaunchedEffect(Unit) {
        while (true) {
            s = prefs.snapshot()
            serviceOn = Util.isServiceEnabled(context)
            notifOn = Util.notificationsGranted(context)
            batteryOn = Util.isIgnoringBattery(context)
            delay(1000)
        }
    }
    fun refresh() {
        s = prefs.snapshot()
        Status.refresh(context, prefs)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = Util.notificationsGranted(context)
        refresh()
    }

    val canEdit = !s.locked
    var sliderValue by remember(s.limitMinutes) { mutableFloatStateOf(s.limitMinutes.toFloat()) }

    var urlText by remember { mutableStateOf(prefs.remoteUrl) }
    var igText by remember { mutableStateOf(prefs.extraInstagramIds) }
    var ytText by remember { mutableStateOf(prefs.extraYoutubeIds) }
    var ruleMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ShortsGuard", style = MaterialTheme.typography.headlineMedium)

        // ---- setup status ----
        Section("Setup status") {
            StatusRow("Accessibility service", serviceOn, "Turn on") { Util.openAccessibilitySettings(context) }
            if (!serviceOn) {
                Text(
                    "If the switch is greyed out (Android 13+), open App info, tap the three-dot menu and " +
                        "choose Allow restricted settings first.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { Util.openAppInfo(context) }) { Text("Open App info") }
            }
            StatusRow("Notifications", notifOn, "Allow") {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            StatusRow("Battery: unrestricted", batteryOn, "Fix") { Util.requestBatteryExemption(context) }
            if (!batteryOn) {
                Text(
                    "Without this, phones like Xiaomi, Oppo, Vivo and Samsung may stop the blocker in the " +
                        "background. Some also need Autostart allowed in App info.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---- today ----
        Section("Today") {
            val usedMin = s.usedSeconds / 60
            if (s.limitMinutes == 0) {
                Text("Reels and Shorts are fully blocked.")
            } else {
                Text("$usedMin of ${s.limitMinutes} min used")
                LinearProgressIndicator(
                    progress = { (s.usedSeconds / (s.limitMinutes * 60f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Blocked ${s.blockedToday} time${if (s.blockedToday == 1) "" else "s"} today. " +
                    "Allowance resets at ${formatHour(s.resetHour)}.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.locked) {
                Text(
                    "Strict mode: settings are locked until " +
                        if (s.sleepActive) "sleep mode ends." else "your allowance resets.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---- allowance ----
        Section("Daily allowance") {
            Text(
                if (sliderValue.toInt() == 0) "Blocked completely"
                else "${sliderValue.toInt()} min per day",
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    prefs.limitMinutes = sliderValue.toInt()
                    refresh()
                },
                valueRange = 0f..120f,
                steps = 23,
                enabled = canEdit,
            )
            Text(
                "Only time spent on the Reels/Shorts screen counts, including a minimised or floating " +
                    "Shorts window. 0 blocks them entirely.",
                style = MaterialTheme.typography.bodySmall,
            )
            HourStepper("Resets at", s.resetHour, canEdit) { prefs.resetHour = it; refresh() }
        }

        // ---- apps ----
        Section("Apps") {
            SwitchRow("Instagram Reels", null, s.blockInstagram, canEdit) { prefs.blockInstagram = it; refresh() }
            SwitchRow("YouTube Shorts", null, s.blockYoutube, canEdit) { prefs.blockYoutube = it; refresh() }
        }

        // ---- sleep mode ----
        Section("Sleep mode") {
            SwitchRow("Block completely at night", "Ignores your allowance during these hours.", s.sleepEnabled, canEdit) {
                prefs.sleepEnabled = it; refresh()
            }
            if (s.sleepEnabled) {
                HourStepper("From", s.sleepStart, canEdit) { prefs.sleepStart = it; refresh() }
                HourStepper("Until", s.sleepEnd, canEdit) { prefs.sleepEnd = it; refresh() }
            }
        }

        // ---- strict mode ----
        Section("Strict mode") {
            SwitchRow(
                "Lock settings when over the limit",
                "Once your allowance is used up (or sleep mode is on) you cannot change settings until it resets.",
                s.strict,
                canEdit,
            ) { prefs.strict = it; refresh() }
            Text(
                "You can still switch the service off in Android's Accessibility settings. Strict mode adds friction, not a hard lock.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- notification & widget ----
        Section("Notification and widget") {
            SwitchRow("Show minutes left in a notification", null, s.liveNotification, true) {
                prefs.liveNotification = it; refresh()
            }
            SwitchRow(
                "Alert me if ShortsGuard is turned off",
                "You get a notification if the accessibility service gets switched off.",
                s.alertWhenOff,
                true,
            ) { prefs.alertWhenOff = it; refresh() }
            Text(
                "Home-screen widget: long-press your home screen, choose Widgets, then ShortsGuard.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- last 7 days ----
        Section("Last 7 days") {
            val maxSec = (s.week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(60)
            Row(
                Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                s.week.forEach { (date, seconds) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${seconds / 60}m", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier
                                .width(24.dp)
                                .height((seconds.toFloat() / maxSec * 60f).coerceAtLeast(2f).dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                        )
                        Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ---- detection rules ----
        Section("Detection rules") {
            Text(
                "If blocking stops working after an Instagram or YouTube update, add the new view IDs " +
                    "here (no rebuild needed). Use the captured IDs under Troubleshooting to find them.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it; prefs.remoteUrl = it },
                label = { Text("Rules file link (optional, https)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    ruleMessage = "Loading..."
                    RemoteRules.fetch(prefs) { ruleMessage = it; refresh() }
                }) { Text("Update now") }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (s.remoteFetchedAt > 0) {
                        "Last updated ${DateFormat.getDateTimeInstance().format(Date(s.remoteFetchedAt))}"
                    } else {
                        "Not loaded yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (ruleMessage.isNotEmpty()) Text(ruleMessage, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = igText,
                onValueChange = { igText = it; prefs.extraInstagramIds = it },
                label = { Text("Extra Instagram view IDs (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ytText,
                onValueChange = { ytText = it; prefs.extraYoutubeIds = it },
                label = { Text("Extra YouTube view IDs (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- troubleshooting ----
        Section("Troubleshooting") {
            SwitchRow(
                "Debug logging",
                "Records which windows and view IDs are on screen whenever Instagram or YouTube is active.",
                s.debug,
                true,
            ) { prefs.debug = it; refresh() }
            if (s.debug) {
                Text(
                    "Minimised Shorts not detected? Turn this on, minimise a Short, wait about 5 seconds, " +
                        "come back here and copy what appears below.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (s.lastDump.isEmpty()) "Nothing captured yet." else s.lastDump,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (s.lastDump.isNotEmpty()) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(s.lastDump)) }) { Text("Copy") }
                }
            }
            TextButton(onClick = onReplaySetup) { Text("Show setup guide again") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(24.dp),
        )
        Text(label, Modifier.weight(1f))
        if (!ok) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange((hour + 23) % 24) }, enabled = enabled) { Text("-") }
        Text(formatHour(hour), Modifier.width(64.dp), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { onChange((hour + 1) % 24) }, enabled = enabled) { Text("+") }
    }
}
