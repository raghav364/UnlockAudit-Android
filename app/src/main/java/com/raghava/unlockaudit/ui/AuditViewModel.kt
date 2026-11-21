package com.raghava.unlockaudit.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raghava.unlockaudit.data.AuditRepository
import com.raghava.unlockaudit.data.DaySummary
import com.raghava.unlockaudit.service.TrackerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuditViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AuditRepository(app)
    val search = MutableStateFlow("")
    val date = MutableStateFlow("")
    val message = MutableStateFlow<String?>(null)
    val permission = MutableStateFlow(repo.hasUsageAccess())
    val requested = MutableStateFlow(repo.requestedTracking())
    val initialized = MutableStateFlow(false)
    val migrationProblems = MutableStateFlow(0)
    val retention = MutableStateFlow(repo.retentionDays())
    val themeMode = MutableStateFlow(
        app.getSharedPreferences("settings_v2", 0).getString("theme", "System") ?: "System"
    )
    val days: StateFlow<List<DaySummary>> = combine(date, search) { date, search ->
        date to search.trim()
    }.flatMapLatest { (date, search) -> repo.days(date, search) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val today: StateFlow<DaySummary?> = repo.days(LocalDate.now().toString(), "")
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val allDays: StateFlow<List<DaySummary>> = repo.days("", "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            try {
                repo.initialize()
                migrationProblems.value = repo.migrationProblemCount()
            } catch (e: Exception) {
                message.value = "Initialization failed: ${e.localizedMessage ?: "unknown error"}"
            } finally {
                initialized.value = true
            }
        }
    }
    fun refresh() {
        permission.value = repo.hasUsageAccess()
        requested.value = repo.requestedTracking()
    }
    fun setSearch(value: String) { search.value = value }
    fun setDate(value: String) { date.value = value }
    fun setTheme(value: String) {
        require(value in listOf("System", "Light", "Dark"))
        themeMode.value = value
        getApplication<Application>().getSharedPreferences("settings_v2", 0)
            .edit().putString("theme", value).apply()
    }
    fun setRetention(days: Int) = viewModelScope.launch {
        try {
            repo.setRetentionDays(days)
            retention.value = days
            message.value = "History retention updated"
        } catch (e: Exception) { message.value = e.message ?: "Couldn't change retention" }
    }
    fun clear() = viewModelScope.launch {
        try {
            // Prevent the running service from saving an in-progress session after deletion.
            getApplication<Application>().stopService(
                Intent(getApplication(), TrackerService::class.java)
            )
            repo.clearHistory()
            requested.value = false
            migrationProblems.value = 0
            message.value = "Tracking stopped and history deleted"
        } catch (e: Exception) { message.value = e.message ?: "Couldn't clear history" }
    }
    fun exportLegacy(uri: Uri) = viewModelScope.launch {
        try { repo.exportLegacyText(uri); message.value = "Original v1.1 text exported" }
        catch (e: Exception) { message.value = "Legacy export failed: ${e.localizedMessage}" }
    }
    fun export(uri: Uri) = viewModelScope.launch {
        try { message.value = "Exported ${repo.exportCsv(uri)} CSV rows" }
        catch (e: Exception) { message.value = "Export failed: ${e.localizedMessage}" }
    }
    fun dismissMessage() { message.value = null }
}
