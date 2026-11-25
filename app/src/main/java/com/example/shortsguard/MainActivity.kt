package com.example.shortsguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        prefs.pruneOld()
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Screen(prefs)
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

fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.contains(context.packageName + "/") && enabled.contains("ShortsBlockerService")
}

@Composable
fun Screen(prefs: Prefs) {
    val context = LocalContext.current
    var s by remember { mutableStateOf(prefs.snapshot()) }
    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }

    // Refresh once a second so the usage numbers stay live while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            s = prefs.snapshot()
            serviceOn = isServiceEnabled(context)
            delay(1000)
        }
    }
    fun refresh() { s = prefs.snapshot() }

    val canEdit = !s.locked
    var sliderValue by remember(s.limitMinutes) { mutableFloatStateOf(s.limitMinutes.toFloat()) }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ShortsGuard", style = MaterialTheme.typography.headlineMedium)

        // ---- status ----
        if (!serviceOn) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Not active yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn on ShortsGuard under Accessibility > Installed apps. " +
                            "If the switch is greyed out (Android 13+), open App info, tap the " +
                            "three-dot menu and choose Allow restricted settings first.",
                    )
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Open Accessibility settings") }
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    }) { Text("Open App info") }
                }
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
            Text("Only time spent on the Reels/Shorts screen counts. 0 blocks them entirely.", style = MaterialTheme.typography.bodySmall)
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

        // ---- debug ----
        Section("Troubleshooting") {
            SwitchRow(
                "Debug logging",
                "If blocking stops working after an app update: turn this on, open Reels/Shorts, and read Logcat (tag ShortsGuard) for the new view IDs.",
                s.debug,
                true,
            ) { prefs.debug = it; refresh() }
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
