package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: Player?,
    resizeMode: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (player == null) {
            Text("Initializing Playback Engine...", color = Color.White)
        } else {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        // Enable default controller
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
    }
}
