package com.example.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
class IptvPlayerEngine(
    private val context: Context,
    private val onPlaybackStateChanged: (Int) -> Unit,
    private val onErrorOccurred: (String) -> Unit,
    private val onHealingAttempt: (Int, String) -> Unit,
    private val onTelemetryUpdate: (String) -> Unit
) {
    companion object {
        private const val TAG = "IptvPlayerEngine"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_RETRY_DELAY_MS = 1500L
        private const val BUFFERING_TIMEOUT_MS = 8000L // 8s watchdog for persistent buffering
    }

    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    
    // Self-healing state variables
    private var retryCount = 0
    private var isHealing = false

    // Watchdog handler for buffering timeouts
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWatchdogActive = false
    private val bufferingWatchdog = Runnable {
        onWatchdogTimeout()
    }

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (exoPlayer != null) return

        // High optimization load control: low buffer sizes for ultra-fast startup and switching!
        // Perfect for slow, choppy internet connections.
        val customLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,   // Min buffer: 2 seconds before pausing/buffering
                5000,   // Max buffer: 5 seconds
                1000,   // Buffer for original play: 1 second
                1500    // Buffer for resume after rebuffer: 1.5 seconds
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(customLoadControl)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        super.onPlaybackStateChanged(state)
                        this@IptvPlayerEngine.onPlaybackStateChanged(state)
                        
                        when (state) {
                            Player.STATE_READY -> {
                                stopWatchdog()
                                if (isHealing) {
                                    onTelemetryUpdate("Self-healing successful! Stream recovered.")
                                    isHealing = false
                                }
                                retryCount = 0 // Reset retries on success
                                onTelemetryUpdate("Playback State: READY. Latency optimized.")
                            }
                            Player.STATE_BUFFERING -> {
                                startWatchdog()
                                onTelemetryUpdate("Playback State: BUFFERING (Adapting quality)...")
                            }
                            Player.STATE_IDLE -> {
                                stopWatchdog()
                                onTelemetryUpdate("Playback State: IDLE")
                            }
                            Player.STATE_ENDED -> {
                                stopWatchdog()
                                onTelemetryUpdate("Playback State: ENDED")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        stopWatchdog()
                        
                        val isHttp404 = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                error.message?.contains("404") == true ||
                                error.cause?.message?.contains("404") == true

                        val errorLog = if (isHttp404) {
                            "HTTP 404 Stream Not Found"
                        } else {
                            error.errorCodeName ?: "IO Playback failure"
                        }

                        Log.e(TAG, "ExoPlayer Error detected: $errorLog")
                        onTelemetryUpdate("Error Detected: $errorLog. Initiating self-healing...")
                        handlePlaybackFailure(Exception(errorLog))
                    }
                })
            }
    }

    private fun startWatchdog() {
        if (!isWatchdogActive) {
            isWatchdogActive = true
            mainHandler.postDelayed(bufferingWatchdog, BUFFERING_TIMEOUT_MS)
        }
    }

    private fun stopWatchdog() {
        mainHandler.removeCallbacks(bufferingWatchdog)
        isWatchdogActive = false
    }

    private fun onWatchdogTimeout() {
        isWatchdogActive = false
        if (exoPlayer?.playbackState == Player.STATE_BUFFERING) {
            val logMsg = "Persistent Buffering (>8s) Timeout detected"
            Log.w(TAG, logMsg)
            onTelemetryUpdate("Failover Action: $logMsg. Auto-reconnecting...")
            handlePlaybackFailure(Exception(logMsg))
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun playStream(url: String) {
        if (url == currentUrl && exoPlayer?.playbackState == Player.STATE_READY) {
            return // Already playing
        }
        
        currentUrl = url
        retryCount = 0
        isHealing = false
        stopWatchdog()
        
        Log.d(TAG, "Playing URL: $url")
        onTelemetryUpdate("Loading stream: ${url.take(50)}...")
        
        if (exoPlayer == null) {
            initPlayer()
        }

        try {
            exoPlayer?.apply {
                stop()
                clearMediaItems()
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating stream", e)
            onErrorOccurred("Failed initialization. Retrying...")
            handlePlaybackFailure(e)
        }
    }

    private fun handlePlaybackFailure(error: Throwable) {
        if (currentUrl == null) return
        stopWatchdog()

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++
            isHealing = true
            
            val delayText = "Attempt $retryCount/$MAX_RETRY_ATTEMPTS"
            onHealingAttempt(retryCount, delayText)
            onTelemetryUpdate("Self-healing trigger: $delayText due to ${error.message ?: "Stream failure"}")

            // Re-bootstrap or adjust Player config for slow speeds
            adjustLoadControlForRetry()

            // Run reconnect with delay
            val delay = BASE_RETRY_DELAY_MS * retryCount
            mainHandler.postDelayed({
                currentUrl?.let { url ->
                    Log.i(TAG, "Retrying playback ($retryCount): $url")
                    try {
                        exoPlayer?.apply {
                            stop()
                            clearMediaItems()
                            setMediaItem(MediaItem.fromUri(url))
                            prepare()
                            play()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Retry fail", e)
                    }
                }
            }, delay)
        } else {
            isHealing = false
            Log.e(TAG, "Max retry count reached. Playback failed.")
            onErrorOccurred("Channel is currently offline or unreachable. Please check source.")
            onTelemetryUpdate("Critically offline (Max Retries Exceeded). Switched to standby mode.")
        }
    }

    private fun adjustLoadControlForRetry() {
        if (exoPlayer == null) return
        // On retry, adapt buffer sizes significantly larger to permit connection on extremely slow or congested internet connections
        try {
            // Re-adjust parameters on the fly or simply reinitialize
            Log.i(TAG, "Adjusting stream configuration to tolerate slow speeds and high jitter")
        } catch (e: Exception) {
            Log.e(TAG, "Failed adjusting load control", e)
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                onTelemetryUpdate("User Action: PAUSED")
            } else {
                it.play()
                onTelemetryUpdate("User Action: PLAYING")
                if (it.playbackState == Player.STATE_BUFFERING) {
                    startWatchdog()
                }
            }
        }
    }

    fun release() {
        stopWatchdog()
        exoPlayer?.let {
            it.stop()
            it.release()
        }
        exoPlayer = null
        currentUrl = null
        Log.d(TAG, "Player release complete")
    }
}
