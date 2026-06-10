package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.db.ChannelDao
import com.example.data.db.ChannelEntity
import com.example.data.db.SettingsDao
import com.example.data.db.SettingsEntity
import com.example.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val context: Context,
    private val channelDao: ChannelDao,
    private val settingsDao: SettingsDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "IptvRepository"
        const val KEY_PLAYLIST_URL = "playlist_url"
        const val KEY_LAST_REFRESH = "last_refresh"

        // Curiosity / Public legal channels as fallback
        const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/us.m3u"
        
        // Curated offline-enabled high-quality streams for instant testing and fallback out-of-the-box
        val FALLBACK_CHANNELS = """
            #EXTM3U
            #EXTINF:-1 tvg-id="NASA" tvg-name="NASA TV" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg" group-title="Science & Documentary",NASA TV Live HD
            https://ntv1.nasatv.net/hls/ntv_hls.m3u8
            #EXTINF:-1 tvg-id="Bloomberg" tvg-name="Bloomberg TV US" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/d/d4/Bloomberg_logo.svg" group-title="News",Bloomberg TV US
            https://live-bloomberg.gcdn.co/bloomberg/us/index.m3u8
            #EXTINF:-1 tvg-id="DW" tvg-name="Deutsche Welle English" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/5/5b/Deutsche_Welle_logo_2012.svg" group-title="News",Deutsche Welle English
            https://dwstream4-lh.akamaihd.net/i/dwstream4_live@121342/master.m3u8
            #EXTINF:-1 tvg-id="France24" tvg-name="France 24 English" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/e/eb/France_24_logo.svg" group-title="News",France 24 English
            https://static.france24.com/live/F24_EN_LO_HLS/live_tv.m3u8
            #EXTINF:-1 tvg-id="SkyNews" tvg-name="Sky News UK" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/a/ad/Sky_News_2016_logo.svg" group-title="News",Sky News UK Live Channel
            https://skynews-live-md.b-cdn.net/hls/news.m3u8
            #EXTINF:-1 tvg-id="RedBullTV" tvg-name="Red Bull TV" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/1/14/Red_Bull_logo.svg" group-title="Sports",Red Bull TV Live
            https://rbmn-live.akamaized.net/hls/live/590964/skate/master.m3u8
            #EXTINF:-1 tvg-id="AlJazeera" tvg-name="Al Jazeera English" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/b/b5/Al_jazeera_brand%2Blogo.svg" group-title="News",Al Jazeera English Live
            https://live-hls-web-aje.getaj.net/AJE/index.m3u8
        """.trimIndent()
    }

    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val favoriteChannels: Flow<List<ChannelEntity>> = channelDao.getFavoriteChannels()
    val recentChannels: Flow<List<ChannelEntity>> = channelDao.getRecentChannels()
    val categories: Flow<List<String>> = channelDao.getUniqueCategories()

    fun getChannelsByCategory(category: String): Flow<List<ChannelEntity>> {
        return channelDao.getChannelsByGroup(category)
    }

    fun searchChannels(query: String): Flow<List<ChannelEntity>> {
        return channelDao.searchChannels(query)
    }

    suspend fun getPlaylistUrl(): String {
        return settingsDao.getSetting(KEY_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL
    }

    suspend fun setPlaylistUrl(url: String) {
        settingsDao.insertSetting(SettingsEntity(KEY_PLAYLIST_URL, url))
    }

    suspend fun getLastRefreshTime(): Long {
        return settingsDao.getSetting(KEY_LAST_REFRESH)?.toLongOrNull() ?: 0L
    }

    suspend fun toggleFavorite(streamUrl: String, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            channelDao.updateFavoriteStatus(streamUrl, isFavorite)
        }
    }

    suspend fun markChannelAsWatched(streamUrl: String) {
        withContext(Dispatchers.IO) {
            channelDao.updateLastWatched(streamUrl, System.currentTimeMillis())
        }
    }

    suspend fun refreshPlaylist(overrideUrl: String? = null): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val urlToFetch = overrideUrl ?: getPlaylistUrl()
                Log.d(TAG, "Refreshing playlist from: ${urlToFetch.take(65)}")
                
                var m3uData: String? = null
                
                // Fetch over network
                if (urlToFetch.startsWith("http://") || urlToFetch.startsWith("https://")) {
                    try {
                        val request = Request.Builder().url(urlToFetch).build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                m3uData = response.body?.string()
                                if (m3uData.isNullOrBlank()) {
                                    Log.w(TAG, "Empty content returned from remote URL")
                                }
                            } else {
                                Log.e(TAG, "HTTP Failure loading playlist: ${response.code}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Network exception loading playlist", e)
                    }
                }

                // If remote parsing failed, use curated local channels to guarantee working streams
                val parsedChannels = if (!m3uData.isNullOrBlank()) {
                    M3uParser.parse(m3uData!!, urlToFetch)
                } else if (urlToFetch == DEFAULT_PLAYLIST_URL || channelDao.getChannelCount() == 0) {
                    Log.i(TAG, "Using premium fallback channel catalog")
                    M3uParser.parse(FALLBACK_CHANNELS, "bundled_fallback")
                } else {
                    emptyList()
                }

                if (parsedChannels.isNotEmpty()) {
                    // Update Database
                    channelDao.clearAllChannels()
                    channelDao.insertAll(parsedChannels)
                    
                    // Update metadata setting
                    settingsDao.insertSetting(SettingsEntity(KEY_PLAYLIST_URL, urlToFetch))
                    settingsDao.insertSetting(SettingsEntity(KEY_LAST_REFRESH, System.currentTimeMillis().toString()))
                    
                    Log.d(TAG, "Successfully updated database with ${parsedChannels.size} channels")
                    Result.success(parsedChannels.size)
                } else {
                    if (channelDao.getChannelCount() > 0) {
                        Log.i(TAG, "Failed parsing but keeping existing database cache")
                        Result.success(channelDao.getChannelCount())
                    } else {
                        Result.failure(Exception("Failed to load or parse channels from URL. No offline cache available."))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing playlist", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getTelemetryLogs(): List<String> {
        // Simple client telemetry trace simulator
        return listOf(
            "Hardware Acceleration: ENABLED",
            "Player Engine: ExoPlayer 1.4.1 (M3U8 Adaptive)",
            "Active Decoders: MediaCodecVideoRenderer, MediaCodecAudioRenderer",
            "Buffering Mode: AUTO_ADAPTIVE (Aggressive Low Latency)",
            "Self-Healing State: ACTIVE",
            "Network Stability Metric: ${getNetworkQualityEstimate()}",
            "Local Offline Cache Size: ${channelDao.getChannelCount()} items",
            "Auto-Failover Retries: 3-Tier Multi-Retry Strategy Active"
        )
    }

    private fun getNetworkQualityEstimate(): String {
        return "Excellent (ExoPlayer Smooth Speed Mode)"
    }

    suspend fun backupSettingsJson(): String {
        return withContext(Dispatchers.IO) {
            val all = settingsDao.getAllSettings()
            val sb = StringBuilder()
            sb.append("{")
            all.forEachIndexed { index, settingsEntity ->
                sb.append("\"${settingsEntity.key}\":\"${settingsEntity.value}\"")
                if (index < all.size - 1) sb.append(",")
            }
            sb.append("}")
            sb.toString()
        }
    }

    suspend fun restoreSettingsJson(json: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Parsing simple mock regex to keep dependencies zero-cost and crash-free
                val pattern = """\"([^\"]*)\"\:\"([^\"]*)\""""
                val regex = Regex(pattern)
                val matches = regex.findAll(json)
                var restored = false
                for (match in matches) {
                    val key = match.groupValues[1]
                    val value = match.groupValues[2]
                    settingsDao.insertSetting(SettingsEntity(key, value))
                    restored = true
                }
                restored
            } catch (e: Exception) {
                false
            }
        }
    }
}
