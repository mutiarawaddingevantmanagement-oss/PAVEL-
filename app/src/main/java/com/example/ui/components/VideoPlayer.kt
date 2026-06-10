package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.PlaybackEngine

@SuppressLint("SetJavaScriptEnabled")
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    streamUrl: String?,
    player: Player?,
    resizeMode: Int,
    playbackEngine: PlaybackEngine,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onLog: (String) -> Unit = {},
    onHealing: (String?) -> Unit = {},
    onError: (String?) -> Unit = {}
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (streamUrl == null) {
            Text("No Active Stream Selected", color = Color.White)
        } else if (playbackEngine == PlaybackEngine.EXOPLAYER) {
            if (player == null) {
                Text("Initializing Native Playback Engine...", color = Color.White)
            } else {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = true
                            this.resizeMode = resizeMode
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != player) {
                            playerView.player = player
                        }
                        if (playerView.resizeMode != resizeMode) {
                            playerView.resizeMode = resizeMode
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Hls.js WebView Player
            val htmlContent = remember(streamUrl) { getHlsJsHtml(streamUrl) }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                onLog("Hls.js VideoPlayer loaded inside WebView")
                                // Set initial resize mode
                                val jsMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> 0
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> 1
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 2
                                    else -> 0
                                }
                                view?.evaluateJavascript("setResizeMode($jsMode);", null)
                            }
                        }

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun postMessage(message: String) {
                                onLog(message)
                            }

                            @JavascriptInterface
                            fun onPlaybackState(state: String, description: String) {
                                when (state) {
                                    "PLAYING" -> {
                                        onHealing(null)
                                        onError(null)
                                        onLog("Hls.js: Playing stream successfully")
                                    }
                                    "BUFFERING" -> {
                                        onLog("Hls.js: Web Player is buffering...")
                                    }
                                    "HEALING" -> {
                                        onHealing(description)
                                    }
                                    "ERROR" -> {
                                        onError(description)
                                    }
                                    "PAUSED" -> {
                                        onLog("Hls.js: Playback paused")
                                    }
                                }
                            }
                        }, "AndroidBridge")

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    // Reactive play/pause status integration
                    if (isPlaying) {
                        webView.evaluateJavascript("play();", null)
                    } else {
                        webView.evaluateJavascript("pause();", null)
                    }

                    // Reactive resize mode updates
                    val jsMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> 0
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> 1
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 2
                        else -> 0
                    }
                    webView.evaluateJavascript("setResizeMode($jsMode);", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun getHlsJsHtml(url: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                body, html {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background-color: #000;
                    overflow: hidden;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                }
                video {
                    width: 100%;
                    height: 100%;
                    border: none;
                    background-color: #000;
                }
                /* Floating Tooltip styling */
                .tooltip {
                    position: absolute;
                    top: 12px;
                    left: 12px;
                    z-index: 100;
                    opacity: 0;
                    pointer-events: none;
                    background: rgba(15, 23, 42, 0.9);
                    border: 1px solid rgba(148, 163, 184, 0.15);
                    border-radius: 8px;
                    padding: 6px 12px;
                    color: #94a3b8;
                    font-size: 11px;
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3), 0 4px 6px -4px rgba(0, 0, 0, 0.3);
                    transition: opacity 0.3s ease;
                }
                body:hover .tooltip {
                    opacity: 1;
                }
                .key-badge {
                    background: #1e293b;
                    border: 1px solid #475569;
                    border-radius: 4px;
                    padding: 1px 5px;
                    color: #f8fafc;
                    font-family: monospace;
                    font-size: 10.5px;
                    font-weight: bold;
                    box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
                }
            </style>
            <!-- Load stable Hls.js CDN -->
            <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.8/dist/hls.min.js"></script>
        </head>
        <body>
            <!-- Help Tooltip Overlay on Hover -->
            <div class="tooltip">
                <span style="color: #38bdf8; font-weight: bold; margin-right: 4px;">Shortcuts:</span>
                <span class="key-badge">Space</span> Play/Pause
                <span style="color: #334155;">•</span>
                <span class="key-badge">M</span> Mute
                <span style="color: #334155;">•</span>
                <span class="key-badge">F</span> Fullscreen
            </div>
            <video id="video" playsinline controls></video>
            <script>
                var video = document.getElementById('video');
                var hls = null;
                var retryCount = 0;
                var maxRetries = 5;
                var streamUrl = "$url";

                function logToAndroid(msg) {
                    if (window.AndroidBridge && window.AndroidBridge.postMessage) {
                        window.AndroidBridge.postMessage(msg);
                    }
                }

                function notifyState(state, desc) {
                    if (window.AndroidBridge && window.AndroidBridge.onPlaybackState) {
                        window.AndroidBridge.onPlaybackState(state, desc || "");
                    }
                }

                video.addEventListener('playing', function() {
                    notifyState("PLAYING", "Stream playing");
                });

                video.addEventListener('pause', function() {
                    notifyState("PAUSED", "Stream paused");
                });

                video.addEventListener('waiting', function() {
                    notifyState("BUFFERING", "Buffering stream...");
                });

                video.addEventListener('error', function(e) {
                    logToAndroid("HTML5 video element error detected");
                });

                function loadStream(url) {
                    if (hls) {
                        hls.destroy();
                        hls = null;
                    }

                    if (Hls.isSupported()) {
                        logToAndroid("Initializing Hls.js engine load...");
                        
                        var config = {
                            enableWorker: true,
                            lowLatencyMode: true,
                            backBufferLength: 30,
                            maxBufferLength: 10,
                            manifestLoadingMaxRetry: 5,
                            manifestLoadingRetryDelay: 1000,
                            levelLoadingMaxRetry: 5,
                            fragLoadingMaxRetry: 5
                        };

                        hls = new Hls(config);
                        hls.loadSource(url);
                        hls.attachMedia(video);

                        hls.on(Hls.Events.MANIFEST_PARSED, function() {
                            logToAndroid("Hls.js: parsed manifest successfully. Auto-playing.");
                            video.play().catch(function(err) {
                                logToAndroid("Autoplay requires interaction check: " + err.message);
                            });
                        });

                        hls.on(Hls.Events.ERROR, function(event, data) {
                            var errorType = data.type;
                            var errorDetails = data.details;
                            var errorFatal = data.fatal;

                            logToAndroid("Hls.js Internal Error: Type: " + errorType + ", Details: " + errorDetails + ", Fatal: " + errorFatal);

                            if (errorFatal) {
                                switch (errorType) {
                                    case Hls.ErrorTypes.NETWORK_ERROR:
                                        logToAndroid("Fatal Network Error! Beginning failover retry...");
                                        notifyState("HEALING", "Failover Network Recovery (Attempt " + retryCount + "/5)");
                                        attemptRecovery();
                                        break;
                                    case Hls.ErrorTypes.MEDIA_ERROR:
                                        logToAndroid("Fatal Media Decoding Error! Calling recoverMediaError()");
                                        notifyState("HEALING", "Self-repair codec error...");
                                        hls.recoverMediaError();
                                        break;
                                    default:
                                        logToAndroid("Fatal Unretryable Error. Recreating stream context.");
                                        notifyState("ERROR", "Fatal playback context error");
                                        break;
                                }
                            }
                        });

                        hls.on(Hls.Events.LEVEL_SWITCHED, function(event, data) {
                            logToAndroid("Switched adaptive stream track. Active tier: " + data.level);
                        });

                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                        logToAndroid("Hls.js unsupported. Using native web browser pipeline fallback.");
                        video.src = url;
                        video.play();
                    } else {
                        logToAndroid("Client cannot decode HLS streaming channels.");
                        notifyState("ERROR", "Decoders absent");
                    }
                }

                function attemptRecovery() {
                    if (retryCount < maxRetries) {
                        retryCount++;
                        var backoffDelay = Math.min(1500 * retryCount, 10000);
                        setTimeout(function() {
                            logToAndroid("Executing retry action " + retryCount + " for URL: " + streamUrl);
                            if (hls) {
                                hls.startLoad();
                            } else {
                                loadStream(streamUrl);
                            }
                        }, backoffDelay);
                    } else {
                        logToAndroid("Max failover retry attempts completed. Stream is unreachable.");
                        notifyState("ERROR", "No network connection or stream offline");
                    }
                }

                // Global keyboard event listeners
                window.addEventListener('keydown', function(e) {
                    var key = e.key.toLowerCase();
                    if (e.key === ' ' || key === 'spacebar') {
                        e.preventDefault();
                        if (video.paused) {
                            video.play().catch(function(){});
                        } else {
                            video.pause();
                        }
                        logToAndroid("Global Keyboard Action: [Space] Play/Pause toggled");
                    } else if (key === 'm') {
                        e.preventDefault();
                        video.muted = !video.muted;
                        logToAndroid("Global Keyboard Action: [M] Toggle Mute (" + (video.muted ? "Muted" : "Unmuted") + ")");
                    } else if (key === 'f') {
                        e.preventDefault();
                        if (document.fullscreenElement || document.webkitFullscreenElement) {
                            if (document.exitFullscreen) {
                                document.exitFullscreen();
                            } else if (document.webkitExitFullscreen) {
                                document.webkitExitFullscreen();
                            }
                        } else {
                            if (video.requestFullscreen) {
                                video.requestFullscreen();
                            } else if (video.webkitRequestFullscreen) {
                                video.webkitRequestFullscreen();
                            }
                        }
                        logToAndroid("Global Keyboard Action: [F] Toggle Fullscreen");
                    }
                });

                function play() {
                    video.play().catch(function(){});
                }

                function pause() {
                    video.pause();
                }

                function setResizeMode(mode) {
                    if (mode === 0) {
                        video.style.objectFit = "contain";
                    } else if (mode === 1) {
                        video.style.objectFit = "fill";
                    } else if (mode === 2) {
                        video.style.objectFit = "cover";
                    }
                }

                // Initial loading trigger
                loadStream(streamUrl);
            </script>
        </body>
        </html>
    """.trimIndent()
}
