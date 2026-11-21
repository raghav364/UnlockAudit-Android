package com.raghava.unlockaudit.ui

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.raghava.unlockaudit.data.DaySummary
import com.raghava.unlockaudit.data.SessionWithEvents
import com.raghava.unlockaudit.service.TrackerService
import kotlinx.coroutines.flow.collect
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen { Home, History, Insights, Settings }
private val tealLight = lightColorScheme(primary = Color(0xFF08666E), secondary = Color(0xFF47636A),
    surface = Color(0xFFF7F9FA), surfaceVariant = Color(0xFFE1EBEC))
private val tealDark = darkColorScheme(primary = Color(0xFF76D6DE), secondary = Color(0xFFADCBD0),
    surface = Color(0xFF11191C), surfaceVariant = Color(0xFF2A3B40))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditApp(
    vm: AuditViewModel, themeMode: String,
    openUsageSettings: () -> Unit,
    startTracking: () -> Unit, stopTracking: () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val palette = if (Build.VERSION.SDK_INT >= 31) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (isDark) tealDark else tealLight
    MaterialTheme(colorScheme = palette, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp)
    )) {
        var screen by remember { mutableStateOf(Screen.Home) }
        val initialized by vm.initialized.collectAsState()
        val permission by vm.permission.collectAsState()
        val isRunning by TrackerService.isRunning.collectAsState()
        val requested by vm.requested.collectAsState()
        val message by vm.message.collectAsState()
        val migrationProblems by vm.migrationProblems.collectAsState()
        LaunchedEffect(isRunning) { vm.refresh() }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(message) {
            if (message != null) {
                snackbar.showSnackbar(message!!)
                vm.dismissMessage()
            }
        }
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (screen == Screen.Home) "Unlock Audit" else screen.name) },
                    actions = {
                        if (screen == Screen.Home) {
                            IconButton(onClick = { screen = Screen.Settings }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { tab ->
                        val icon = when (tab) {
                            Screen.Home -> Icons.Outlined.Home
                            Screen.History -> Icons.Outlined.FolderOpen
                            Screen.Insights -> Icons.Outlined.BarChart
                            Screen.Settings -> Icons.Outlined.Settings
                        }
                        NavigationBarItem(
                            selected = screen == tab, onClick = { screen = tab },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(tab.name) }, alwaysShowLabel = true
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { inner ->
            if (!initialized) {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (screen) {
                    Screen.Home -> HomeScreen(
                        vm, Modifier.padding(inner), isRunning, requested, permission,
                        migrationProblems, openUsageSettings, startTracking, stopTracking,
                        openHistory = { screen = Screen.History }
                    )
                    Screen.History -> HistoryScreen(vm, Modifier.padding(inner))
                    Screen.Insights -> InsightsScreen(vm, Modifier.padding(inner))
                    Screen.Settings -> SettingsScreen(
                        vm, Modifier.padding(inner), permission, openUsageSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    vm: AuditViewModel, modifier: Modifier, isRunning: Boolean, requested: Boolean,
    permission: Boolean, migrationProblems: Int,
    openUsageSettings: () -> Unit, startTracking: () -> Unit,
    stopTracking: () -> Unit, openHistory: () -> Unit
) {
    val today by vm.today.collectAsState()
    val days by vm.allDays.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Your activity, organized.", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold)
            Text("Private, on-device unlock history", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isRunning) Icons.Outlined.CheckCircle else Icons.Outlined.PauseCircle,
                            contentDescription = null,
                            tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isRunning) "Tracking is active" else if (requested) "Tracking interrupted" else "Tracking is off",
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isRunning) "A foreground service records sessions; enable notifications to see its status"
                                else if (requested) "Reopen tracking to resume; interrupted sessions are flagged"
                                else "Start only when you want to record unlock sessions",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (!permission) {
                        Text("Usage Access must be granted to see app activity.",
                            color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = openUsageSettings) { Text("Grant Usage Access") }
                    }
                    Button(
                        onClick = if (isRunning) stopTracking else startTracking,
                        modifier = Modifier.fillMaxWidth(), enabled = permission
                    ) {
                        Icon(if (isRunning) Icons.Outlined.StopCircle else Icons.Outlined.PlayArrow,
                            contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRunning) "Stop tracking" else "Start tracking")
                    }
                }
            }
        }
        if (migrationProblems > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("$migrationProblems older history record(s) could not be parsed. The original v1.1 text remains stored for recovery.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        item {
            Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SummaryRow(today)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent days", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = openHistory) { Text("All history") }
            }
        }
        if (days.isEmpty()) item { EmptyState("No activity recorded yet", "Unlock your phone, open an app, then turn the screen off.") }
        items(days.take(5), key = { it.localDate }) { day ->
            DayCard(day, vm, search = "")
        }
    }
}

@Composable
private fun SummaryRow(day: DaySummary?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Sessions", "${day?.sessionCount ?: 0}", Modifier.weight(1f))
        StatTile("Session time", prettyDuration(day?.totalDurationMs ?: 0), Modifier.weight(1f))
        StatTile("Apps", "${day?.appCount ?: 0}", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: AuditViewModel, modifier: Modifier) {
    val days by vm.days.collectAsState()
    val search by vm.search.collectAsState()
    val date by vm.date.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    if (showCalendar) {
        val picker = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showCalendar = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { ms ->
                    vm.setDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                showCalendar = false
            }) { Text("Choose") }
        }, dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancel") } }) {
            DatePicker(state = picker)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Browse your activity", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search, onValueChange = vm::setSearch,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search app name or package") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotBlank()) IconButton(onClick = { vm.setSearch("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showCalendar = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (date.isBlank()) "Choose a date" else date)
                }
                if (date.isNotBlank()) TextButton(onClick = { vm.setDate("") }) { Text("All dates") }
            }
        }
        if (days.isEmpty()) item {
            EmptyState("No matching days", "Try another date or search term.")
        }
        items(days, key = { it.localDate }) { day ->
            DayCard(day, vm, search)
        }
    }
}

@Composable
private fun DayCard(day: DaySummary, vm: AuditViewModel, search: String) {
    var expanded by remember(day.localDate) { mutableStateOf(false) }
    var visibleCount by remember(day.localDate) { mutableIntStateOf(30) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(friendlyDate(day.localDate), fontWeight = FontWeight.SemiBold)
                    Text("${day.sessionCount} sessions · ${prettyDuration(day.totalDurationMs)} · ${day.appCount} apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse day" else "Expand day")
            }
            if (expanded) {
                HorizontalDivider()
                val sessions by produceState<List<SessionWithEvents>?>(null, day.localDate, search) {
                    vm.repo.sessionsForDay(day.localDate, search).collect { value = it }
                }
                when {
                    sessions == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    sessions!!.isEmpty() -> Text(
                        "No sessions match this filter.",
                        Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> {
                        sessions!!.take(visibleCount).forEach { session -> SessionCard(session) }
                        if (sessions!!.size > visibleCount) {
                            TextButton(
                                onClick = { visibleCount += 30 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Show more sessions (${sessions!!.size - visibleCount} remaining)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(entry: SessionWithEvents) {
    val data = entry.session
    var showEvents by remember(data.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { showEvents = !showEvents }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${shortTime(data.startedAt)} – ${shortTime(data.endedAt)}",
                    style = MaterialTheme.typography.titleSmall)
                Text(
                    "${if (data.interrupted) "Interrupted · duration not counted" else prettyDuration(data.endedAt - data.startedAt)}"
                        + " · ${entry.events.distinctBy { it.packageName }.size} apps"
                        + if (data.source == "legacy") " · Imported" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(if (showEvents) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (showEvents) "Hide session details" else "Show session details")
        }
        if (showEvents) {
            if (entry.events.isEmpty()) {
                Text("No app transitions recorded. Usage Access might have been unavailable.",
                    Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
            } else {
                entry.events.forEach { event ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 30.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(event.packageName)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.appLabel, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (event.appLabel != event.packageName) Text(event.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(shortTime(event.occurredAt), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val bitmap = remember(pkg) {
        try { context.packageManager.getApplicationIcon(pkg).toBitmap(44, 44).asImageBitmap() }
        catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(BitmapPainter(bitmap), contentDescription = null, modifier = Modifier.size(30.dp))
    } else {
        Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun InsightsScreen(vm: AuditViewModel, modifier: Modifier) {
    val days by vm.allDays.collectAsState()
    val recent = days.take(7)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Your activity insights", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Text("Recorded session time is unlock-to-screen-off time, not per-app screen time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SummaryRow(DaySummary(
                localDate = "", sessionCount = recent.sumOf { it.sessionCount },
                totalDurationMs = recent.sumOf { it.totalDurationMs },
                appCount = recent.maxOfOrNull { it.appCount } ?: 0
            ))
            Spacer(Modifier.height(5.dp))
            Text("Last 7 recorded days · Apps shows the highest daily count",
                style = MaterialTheme.typography.labelSmall)
        }
        item { Text("Daily breakdown", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold) }
        if (recent.isEmpty()) item { EmptyState("No insights yet", "Start tracking to see your daily activity.") }
        items(recent, key = { it.localDate }) { day ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text(friendlyDate(day.localDate), modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold)
                        Text("${day.sessionCount} sessions")
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            (day.totalDurationMs.toFloat() /
                                (recent.maxOfOrNull { it.totalDurationMs } ?: 1L).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${prettyDuration(day.totalDurationMs)} recorded · ${day.appCount} distinct apps",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AuditViewModel, modifier: Modifier, permission: Boolean,
                           openUsageSettings: () -> Unit) {
    val retention by vm.retention.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val migration by vm.migrationProblems.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var retentionMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let(vm::export)
    }
    val legacyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        it?.let(vm::exportLegacy)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all history?") },
            text = { Text("This permanently deletes recorded sessions and legacy history stored in the app. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Privacy and preferences", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold) }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (permission) "Usage Access granted" else "Usage Access not granted")
                    OutlinedButton(onClick = openUsageSettings) { Text("Open Usage Access settings") }
                    Text("Tracking records app foreground events and unlock-to-screen-off sessions only.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box {
                        TextButton(onClick = { themeMenu = true }) { Text("Theme: $theme  ▾") }
                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                            listOf("System", "Light", "Dark").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = {
                                    vm.setTheme(option); themeMenu = false
                                })
                            }
                        }
                    }
                    Text("System follows your phone's light or dark theme.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp)) {
                    Text("Data retention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box {
                        TextButton(onClick = { retentionMenu = true }) {
                            Text("Keep: ${if (retention == 0) "Forever" else "$retention days"}  ▾")
                        }
                        DropdownMenu(expanded = retentionMenu, onDismissRequest = { retentionMenu = false }) {
                            listOf(30, 90, 365, 0).forEach { days ->
                                DropdownMenuItem(
                                    text = { Text(if (days == 0) "Forever" else "$days days") },
                                    onClick = { vm.setRetention(days); retentionMenu = false }
                                )
                            }
                        }
                    }
                    Text("Older sessions are automatically deleted after each new session and on launch.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("No account or internet permission. History stays on this device; Android backup and device transfer are disabled.",
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { exportLauncher.launch("unlock-audit-${LocalDate.now()}.csv") },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Export history as CSV")
                    }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Delete all history")
                    }
                }
            }
        }
        if (migration > 0) item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Legacy history needs attention", color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                    Text("$migration old record(s) could not be parsed. Export your preserved v1.1 text to review or recover them.")
                    OutlinedButton(onClick = { legacyExportLauncher.launch("unlock-audit-v1-original.txt") }) {
                        Text("Export original v1.1 history")
                    }
                }
            }
        }
        item {
            Text("Unlock Audit 2.0 · Local-only activity journal · Version 3",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun prettyDuration(millis: Long): String {
    val mins = millis.coerceAtLeast(0) / 60_000
    return when {
        mins >= 60 -> "${mins / 60}h ${mins % 60}m"
        mins > 0 -> "${mins}m"
        else -> "${(millis.coerceAtLeast(0) / 1000)}s"
    }
}
private fun shortTime(millis: Long) = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
private fun friendlyDate(date: String): String {
    val parsed = try { LocalDate.parse(date) } catch (_: Exception) { return date }
    return when (parsed) {
        LocalDate.now() -> "Today · ${parsed.format(DateTimeFormatter.ofPattern("MMM d"))}"
        LocalDate.now().minusDays(1) -> "Yesterday · ${parsed.format(DateTimeFormatter.ofPattern("MMM d"))}"
        else -> parsed.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }
}
