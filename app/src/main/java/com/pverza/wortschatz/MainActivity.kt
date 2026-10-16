package com.pverza.wortschatz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Reminders.reschedule(this)
        val store = Store(applicationContext)
        setContent {
            WortschatzTheme {
                App(store)
            }
        }
    }
}

@Composable
fun WortschatzTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF9CC2FF))
    } else {
        lightColorScheme(primary = Color(0xFF1F3A5F))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@Composable
fun App(store: Store) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (!hasNotificationPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Study") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text("Progress") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> StudyScreen(store)
                1 -> ProgressScreen(store)
                else -> SettingsScreen(store)
            }
        }
    }
}

@Composable
fun StudyScreen(store: Store) {
    val context = LocalContext.current
    var current by remember {
        mutableStateOf(Picker.next(context, store, System.currentTimeMillis(), allowAhead = true))
    }
    var revealed by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }

    val answer: (Boolean) -> Unit = { known ->
        current?.let { store.answer(it.id, known) }
        done += 1
        revealed = false
        current = Picker.next(context, store, System.currentTimeMillis(), allowAhead = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Wortschatz",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "$done reviewed this session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        val word = current
        if (word == null) {
            Text(
                "Nothing to study right now. Turn on more levels in Settings.",
                textAlign = TextAlign.Center,
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { revealed = !revealed }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        word.level,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        word.de,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    if (revealed) {
                        Text(
                            word.en,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            word.ex,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            word.exEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            "Tap to reveal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (revealed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { answer(false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Still learning") }
                    Button(
                        onClick = { answer(true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("I know it") }
                }
            } else {
                Button(
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Show answer") }
            }
        }
    }
}

@Composable
fun ProgressScreen(store: Store) {
    val context = LocalContext.current
    val words = remember { WordRepository.all(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your progress", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        for (level in listOf("A1", "A2", "B1")) {
            val inLevel = words.filter { it.level == level }
            val seen = inLevel.count { store.progress(it.id) != null }
            val learned = inLevel.count { (store.progress(it.id)?.box ?: 0) >= Store.LEARNED_BOX }
            val fraction = if (inLevel.isEmpty()) 0f else learned.toFloat() / inLevel.size

            Text("Level $level", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$learned learned · $seen seen · ${inLevel.size} words",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "A word counts as learned once you have recalled it several times in a row.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsScreen(store: Store) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(store.notificationsEnabled) }
    var interval by remember { mutableIntStateOf(store.intervalHours) }
    var start by remember { mutableIntStateOf(store.startHour) }
    var end by remember { mutableIntStateOf(store.endHour) }
    var newPerDay by remember { mutableIntStateOf(store.newPerDay) }
    var levels by remember { mutableStateOf(store.levels) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Word cards in notifications", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = enabled,
                onCheckedChange = { value ->
                    enabled = value
                    store.notificationsEnabled = value
                    Reminders.reschedule(context)
                    if (value && !hasNotificationPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        Text("Show a new card every", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (hours in listOf(1, 2, 3, 4, 6)) {
                FilterChip(
                    selected = interval == hours,
                    onClick = {
                        interval = hours
                        store.intervalHours = hours
                        Reminders.reschedule(context)
                    },
                    label = { Text("${hours}h") },
                )
            }
        }
        Text(
            "Longer intervals use less battery.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Active hours: %02d:00 – %02d:00".format(start, end),
            style = MaterialTheme.typography.titleMedium,
        )
        Text("From", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = start.toFloat(),
            onValueChange = { raw ->
                start = raw.roundToInt()
                if (end <= start) end = start + 1
                store.startHour = start
                store.endHour = end
            },
            valueRange = 0f..23f,
            steps = 22,
        )
        Text("Until", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = end.toFloat(),
            onValueChange = { raw ->
                end = raw.roundToInt()
                if (end <= start) start = end - 1
                store.startHour = start
                store.endHour = end
            },
            valueRange = 1f..24f,
            steps = 22,
        )

        Spacer(Modifier.height(8.dp))
        Text("Levels", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in listOf("A1", "A2", "B1")) {
                FilterChip(
                    selected = level in levels,
                    onClick = {
                        val updated = if (level in levels) levels - level else levels + level
                        if (updated.isNotEmpty()) {
                            levels = updated
                            store.levels = updated
                        }
                    },
                    label = { Text(level) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("New words per day", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (count in listOf(5, 10, 20)) {
                FilterChip(
                    selected = newPerDay == count,
                    onClick = {
                        newPerDay = count
                        store.newPerDay = count
                    },
                    label = { Text("$count") },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                val word = Picker.next(context, store, System.currentTimeMillis(), allowAhead = true)
                if (word != null) {
                    store.markShown(word.id)
                    Notifier.show(context, word)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send a test card now") }

        Spacer(Modifier.height(8.dp))
        Text(
            "No ads, no tracking, no internet access. Cards are quiet: no sound or vibration, " +
                "and a new card replaces the previous one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
