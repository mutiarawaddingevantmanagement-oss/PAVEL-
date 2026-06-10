package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.db.ChannelEntity
import com.example.ui.components.VideoPlayer
import com.example.ui.components.CategorySidebar
import com.example.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.ui.AspectRatioFrameLayout

sealed class Screen(val title: String) {
    object LiveTv : Screen("Live TV")
    object Favorites : Screen("Favorites")
    object Settings : Screen("Admin Panel")
    object Telemetry : Screen("Logs & Diagnostics")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvApp(
    viewModel: IptvViewModel = viewModel(),
    onFullScreenToggle: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.LiveTv) }
    var isSidebarOpen by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Init ExoPlayer
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initPlayerEngine(context)
    }

    // Handles simple toast notifications
    state.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            // Display toast or snackbar
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlueBg)
    ) {
        if (isLandscape && state.selectedChannel != null) {
            // Premium Immersive Fullscreen playback mode in Landscape orientation
            Box(modifier = Modifier.fillMaxSize()) {
                VideoPlayer(
                    streamUrl = state.selectedChannel?.streamUrl,
                    player = viewModel.getPlayer(),
                    resizeMode = resizeMode,
                    playbackEngine = state.playbackEngine,
                    isPlaying = state.isPlaying,
                    onLog = { viewModel.logFromWebView(it) },
                    onHealing = { viewModel.setHealingProgress(it) },
                    onError = { viewModel.addTelemetryLog("[Hls.js Error] $it") },
                    modifier = Modifier.fillMaxSize()
                )

                // Landscape Quick Overlay Controls
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                // Back to standard view
                                onFullScreenToggle(false)
                                viewModel.selectChannel(state.selectedChannel!!) // standard reload
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Exit Fullscreen", tint = TextWhite)
                        }

                        // Overlay Title
                        Text(
                            text = state.selectedChannel?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )

                        // Resize Mode Control Toggle
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                val modeName = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit Window"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
                                    else -> "Zoom Crop"
                                }
                                viewModel.showToast("Aspect Ratio: $modeName")
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = AccentCyan)
                        }
                    }
                }

                // Healing Indicator Overlay
                state.healingProgress?.let { healingText ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .border(1.dp, AccentCyan, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(healingText, color = AccentCyan, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            // Standard Multi-Pane Portrait or Non-immersive interface
            Scaffold(
                containerColor = DeepBlueBg,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (currentScreen == Screen.LiveTv) {
                                IconButton(
                                    onClick = { isSidebarOpen = !isSidebarOpen },
                                    modifier = Modifier.testTag("sidebar_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isSidebarOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                                        contentDescription = "Toggle Sidebar",
                                        tint = AccentCyan
                                    )
                                }
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tv,
                                    contentDescription = "Pavel Logo",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "PAVEL IPTV",
                                        fontWeight = FontWeight.Black,
                                        color = TextWhite,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        "High-Speed Smart Player",
                                        color = TextGray,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DeepBlueSurface,
                            titleContentColor = TextWhite
                        ),
                        actions = {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.dp
                                )
                            }
                            IconButton(onClick = { viewModel.triggerPlaylistRefresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = AccentCyan)
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = DeepBlueSurface,
                        contentColor = TextGray
                    ) {
                        val items = listOf(
                            Screen.LiveTv to Icons.Default.Tv,
                            Screen.Favorites to Icons.Default.Favorite,
                            Screen.Telemetry to Icons.Default.Info,
                            Screen.Settings to Icons.Default.Security
                        )
                        items.forEach { (screen, icon) ->
                            NavigationBarItem(
                                icon = { Icon(icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentCyan,
                                    selectedTextColor = AccentCyan,
                                    indicatorColor = PrimaryBlue,
                                    unselectedIconColor = TextGray,
                                    unselectedTextColor = TextGray
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen) {
                        is Screen.LiveTv -> LiveTvView(viewModel, state, resizeMode, isSidebarOpen) { resizeMode = it }
                        is Screen.Favorites -> FavoritesView(viewModel, state)
                        is Screen.Telemetry -> TelemetryView(state)
                        is Screen.Settings -> SettingsView(viewModel, state)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTvView(
    viewModel: IptvViewModel,
    state: IptvUiState,
    resizeMode: Int,
    isSidebarOpen: Boolean,
    onResizeModeChange: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            CategorySidebar(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                channels = state.channels,
                selectedChannel = state.selectedChannel,
                onCategorySelect = { viewModel.selectCategory(it) },
                onChannelSelect = { viewModel.selectChannel(it) },
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        // Active Stream Player Panel (Adaptive top area)
        if (state.selectedChannel != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoPlayer(
                        streamUrl = state.selectedChannel?.streamUrl,
                        player = viewModel.getPlayer(),
                        resizeMode = resizeMode,
                        playbackEngine = state.playbackEngine,
                        isPlaying = state.isPlaying,
                        onLog = { viewModel.logFromWebView(it) },
                        onHealing = { viewModel.setHealingProgress(it) },
                        onError = { viewModel.addTelemetryLog("[Hls.js Error] $it") },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Error Notification / Self Healing Dialog banner
                    if (state.errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Stream error", tint = Color.Red, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    state.errorMessage ?: "Playback Error",
                                    color = TextWhite,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    Button(
                                        onClick = { viewModel.clearPlaybackError() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text("Ignore")
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Button(
                                        onClick = { viewModel.selectChannel(state.selectedChannel) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                                    ) {
                                        Text("Force Reconnect")
                                    }
                                }
                            }
                        }
                    }

                    // Bottom info label overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedChannel.name,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.selectedChannel.groupName,
                                color = TextGray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Player controls row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.togglePlayPause() }) {
                                Icon(
                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle playState",
                                    tint = AccentCyan
                                )
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(state.selectedChannel) }) {
                                Icon(
                                    if (state.selectedChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle favorite state",
                                    tint = if (state.selectedChannel.isFavorite) Color.Red else TextGray
                                )
                            }
                            IconButton(
                                onClick = {
                                    val nextMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    onResizeModeChange(nextMode)
                                }
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Toggle aspect ratio", tint = TextWhite)
                            }
                        }
                    }

                    // Self healing text status indicator
                    state.healingProgress?.let { healing ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color.Black.copy(alpha = 0.8f))
                                .border(1.dp, AccentCyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(healing, color = AccentCyan, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else {
            // Interactive visual landing screen if no channel loaded
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DeepBlueSurface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Tv, contentDescription = "TV Logo", tint = AccentCyan, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Select a channel to begin ultra-fast streaming",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Compatible HLS adaptive streaming active.",
                            color = TextGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Playback Engine selector row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DeepBlueSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Engine Select",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Decoder Engine:",
                        color = TextWhite,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaybackEngine.values().forEach { engine ->
                        val isSelected = state.playbackEngine == engine
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) AccentCyan else Color(0xFF0F172A))
                                .clickable { viewModel.setPlaybackEngine(engine) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = engine.label,
                                color = if (isSelected) DeepBlueBg else TextWhite,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Live Channels Catalog UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search channel instantly...", color = TextGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = AccentCyan) },
                textStyle = androidx.compose.ui.text.TextStyle(color = TextWhite),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = DeepBlueSurface,
                    unfocusedContainerColor = DeepBlueSurface
                )
            )
            if (state.searchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextWhite)
                }
            }
        }

        // Category scrolling slider bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.categories) { cat ->
                val selected = cat == state.selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AccentCyan else DeepBlueSurface)
                        .clickable { viewModel.selectCategory(cat) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (selected) DeepBlueBg else TextWhite,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Display results matching parameters
        val renderedChannels = state.channels.filter { ch ->
            // Search Match
            val matchesSearch = ch.name.contains(state.searchQuery, ignoreCase = true) || 
                                ch.groupName.contains(state.searchQuery, ignoreCase = true)
            // Category Match
            val matchesCategory = state.selectedCategory == "All" || ch.groupName == state.selectedCategory
            
            matchesSearch && matchesCategory
        }

        if (renderedChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = "Empty results", tint = TextGray, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No channels found matching inputs", color = TextGray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 165.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(renderedChannels, key = { it.streamUrl }) { channel ->
                    ChannelGridCard(
                        channel = channel,
                        isSelected = state.selectedChannel?.streamUrl == channel.streamUrl,
                        onClick = { viewModel.selectChannel(channel) }
                    )
                }
            }
        }
        }
    }
}

@Composable
fun ChannelGridCard(
    channel: ChannelEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                1.dp,
                if (isSelected) AccentCyan else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepBlueSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DeepBlueBg),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = "${channel.name} Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = "Backup icon",
                        tint = AccentCyan.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = channel.name,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.groupName,
                color = TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun FavoritesView(
    viewModel: IptvViewModel,
    state: IptvUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "My Favorite Channels",
            color = TextWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Quick access to starred stations",
            color = TextGray,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (state.favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "No favs", tint = TextGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No favorites saved yet", color = TextGray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.favorites, key = { it.streamUrl }) { fav ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DeepBlueSurface)
                            .clickable { viewModel.selectChannel(fav) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeepBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (fav.logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = fav.logoUrl,
                                    contentDescription = fav.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = AccentCyan)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fav.name, color = TextWhite, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(fav.groupName, color = TextGray, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { viewModel.toggleFavorite(fav) }) {
                            Icon(Icons.Default.Favorite, contentDescription = "Filled favorite icon", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryView(state: IptvUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "System Diagnostic Logging",
            color = TextWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Real-time telemetries, codecs, and connection self-heal trace",
            color = TextGray,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        // System telemetry overview stats
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepBlueSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Player Status: ${if (state.isPlaying) "ONLINE (Streaming)" else "STANDBY / buffering"}",
                    color = if (state.isPlaying) SuccessGreen else AccentCyan,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Total Cache Count: ${state.channels.size} live streams", color = TextWhite, style = MaterialTheme.typography.bodySmall)
                Text("Database Version: Room v2.7.0 (Fall-back enabled)", color = TextWhite, style = MaterialTheme.typography.bodySmall)
                Text("Active Network Quality: Stable Optimized HLS", color = TextWhite, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Console Log Feed:", color = TextWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Virtual interactive console log terminal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            if (state.telemetryLogs.isEmpty()) {
                Text("Console boot trace empty.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.telemetryLogs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("Error")) Color.Red else if (log.contains("Successful") || log.contains("recovered")) SuccessGreen else Color.Green,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    viewModel: IptvViewModel,
    state: IptvUiState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepBlueSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large styled locker/security key icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(Color.Red.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Access Blocked",
                        tint = Color.Red,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Administrative Access Blocked",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Admin Panel is available only on Desktop or Laptop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Security Isolation Enforcement",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "The Pavel IPTV ecosystem separates consumer playback interfaces from management tools. Platform environments such as Android Mobile, Android TV, Google TV, Fire TV, and iOS apps are restricted from direct administrative functions (such as custom M3U configuration, user profile tables, analytics tracking, and credential back-ups). Please log in to the Desktop Console Web portal to manage channels, playlists, or database configurations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Badge(containerColor = Color(0xFF0F172A), contentColor = AccentCyan, text = "JWT SECURE")
                    Badge(containerColor = Color(0xFF0F172A), contentColor = AccentCyan, text = "IP RESTRICTED")
                    Badge(containerColor = Color(0xFF0F172A), contentColor = AccentCyan, text = "MFA LOGGED")
                }
            }
        }
    }
}

@Composable
private fun Badge(containerColor: Color, contentColor: Color, text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
}
