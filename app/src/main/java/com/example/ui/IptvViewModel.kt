package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ChannelEntity
import com.example.data.repository.IptvRepository
import com.example.player.IptvPlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IptvUiState(
    val isLoading: Boolean = false,
    val channels: List<ChannelEntity> = emptyList(),
    val favorites: List<ChannelEntity> = emptyList(),
    val recents: List<ChannelEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val selectedChannel: ChannelEntity? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val playlistUrl: String = "",
    val lastRefreshTime: Long = 0L,
    val telemetryLogs: List<String> = emptyList(),
    val healingProgress: String? = null
)

class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val repository = IptvRepository(application, db.channelDao(), db.settingsDao())

    private val _uiState = MutableStateFlow(IptvUiState())
    val uiState: StateFlow<IptvUiState> = _uiState.asStateFlow()

    private var playerEngine: IptvPlayerEngine? = null

    init {
        // Observe Channels, Favorites, Recents, and Categories
        observeData()
        
        // Initial load of configurations and playlists
        viewModelScope.launch {
            val url = repository.getPlaylistUrl()
            val lastRefresh = repository.getLastRefreshTime()
            _uiState.update { it.copy(playlistUrl = url, lastRefreshTime = lastRefresh) }
            
            // Try refreshing or loading cache
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.refreshPlaylist()
            if (result.isFailure) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Playlist failed to load: ${result.exceptionOrNull()?.message}"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, lastRefreshTime = System.currentTimeMillis()) }
                // Pull logs
                updateTelemetryLogs()
            }
        }
    }

    private fun observeData() {
        // Collect reactive channels
        viewModelScope.launch {
            repository.allChannels.collect { allCh ->
                _uiState.update { it.copy(channels = allCh) }
            }
        }

        // Collect favorites
        viewModelScope.launch {
            repository.favoriteChannels.collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }

        // Collect recents
        viewModelScope.launch {
            repository.recentChannels.collect { recs ->
                _uiState.update { it.copy(recents = recs) }
            }
        }

        // Collect categories
        viewModelScope.launch {
            repository.categories.collect { cats ->
                val fullCategories = listOf("All") + cats.filter { it.isNotBlank() }
                _uiState.update { it.copy(categories = fullCategories) }
            }
        }
    }

    fun initPlayerEngine(context: android.content.Context) {
        if (playerEngine != null) return
        
        playerEngine = IptvPlayerEngine(
            context = context,
            onPlaybackStateChanged = { state ->
                val isPlaying = state == androidx.media3.common.Player.STATE_READY && playerEngine?.getPlayer()?.isPlaying == true
                _uiState.update { it.copy(isPlaying = isPlaying) }
            },
            onErrorOccurred = { err ->
                _uiState.update { it.copy(errorMessage = err, healingProgress = null) }
            },
            onHealingAttempt = { attempt, text ->
                _uiState.update { it.copy(healingProgress = "Self-Healing: $text (Reconnecting...)") }
            },
            onTelemetryUpdate = { log ->
                addTelemetryLog(log)
            }
        )
        addTelemetryLog("IPTV Playback Engine Initialized.")
    }

    fun getPlayer() = playerEngine?.getPlayer()

    fun selectChannel(channel: ChannelEntity) {
        _uiState.update { it.copy(selectedChannel = channel, errorMessage = null, healingProgress = null) }
        addTelemetryLog("User switched channel to: ${channel.name}")
        
        playerEngine?.playStream(channel.streamUrl)
        
        // Save to recently watched
        viewModelScope.launch {
            repository.markChannelAsWatched(channel.streamUrl)
        }
    }

    fun togglePlayPause() {
        playerEngine?.togglePlayPause()
        _uiState.update { it.copy(isPlaying = playerEngine?.getPlayer()?.isPlaying == true) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.streamUrl, !channel.isFavorite)
            // Update selectedChannel status in state if it's the currently playing one
            if (_uiState.value.selectedChannel?.streamUrl == channel.streamUrl) {
                _uiState.update { 
                    it.copy(selectedChannel = it.selectedChannel?.copy(isFavorite = !channel.isFavorite))
                }
            }
            showToast("Updated favorites for ${channel.name}")
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        addTelemetryLog("Filtered category: $category")
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updatePlaylistUrl(url: String) {
        _uiState.update { it.copy(playlistUrl = url) }
        viewModelScope.launch {
            repository.setPlaylistUrl(url)
        }
    }

    fun triggerPlaylistRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            addTelemetryLog("Manual playlist refresh requested.")
            val result = repository.refreshPlaylist(_uiState.value.playlistUrl)
            _uiState.update { it.copy(isLoading = false) }
            
            if (result.isSuccess) {
                _uiState.update { it.copy(lastRefreshTime = System.currentTimeMillis()) }
                showToast("Playlist updated successfully! Loaded ${result.getOrNull()} streams.")
                addTelemetryLog("Playlist updated successfully. Size: ${result.getOrNull()} items.")
            } else {
                _uiState.update { it.copy(errorMessage = "Update failed: ${result.exceptionOrNull()?.message}") }
                addTelemetryLog("Playlist refresh failed.")
            }
        }
    }

    fun exportSettings(): String {
        var json = ""
        viewModelScope.launch {
            json = repository.backupSettingsJson()
        }
        return json
    }

    fun importSettings(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val ok = repository.restoreSettingsJson(json)
            if (ok) {
                val url = repository.getPlaylistUrl()
                _uiState.update { it.copy(playlistUrl = url) }
                repository.refreshPlaylist(url)
                showToast("Settings and playlists imported successfully!")
                addTelemetryLog("Database configuration restored from backup.")
            } else {
                showToast("Invalid backup JSON structure.")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearPlaybackError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun addTelemetryLog(log: String) {
        val currentLogs = _uiState.value.telemetryLogs.toMutableList()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        currentLogs.add(0, "[$timestamp] $log") // Keep latests at start
        if (currentLogs.size > 80) {
            currentLogs.removeLast()
        }
        _uiState.update { it.copy(telemetryLogs = currentLogs) }
    }

    private fun updateTelemetryLogs() {
        viewModelScope.launch {
            val systemLogs = repository.getTelemetryLogs()
            systemLogs.forEach { addTelemetryLog(it) }
        }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine?.release()
        playerEngine = null
    }
}
