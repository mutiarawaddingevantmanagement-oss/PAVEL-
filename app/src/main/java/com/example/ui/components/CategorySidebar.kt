package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.ChannelEntity
import com.example.ui.theme.*

@Composable
fun CategorySidebar(
    categories: List<String>,
    selectedCategory: String,
    channels: List<ChannelEntity>,
    selectedChannel: ChannelEntity?,
    onCategorySelect: (String) -> Unit,
    onChannelSelect: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var genreSearchQuery by remember { mutableStateOf("") }
    // Keeps track of which categories are expanded in the Accordion view
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize the selected category as expanded
    LaunchedEffect(selectedCategory) {
        if (selectedCategory.isNotBlank()) {
            expandedCategories[selectedCategory] = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DeepBlueSurface)
            .padding(12.dp)
            .testTag("category_sidebar")
    ) {
        // Sidebar Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = "Genres",
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Genres & Channels",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        }

        // Inline minimal search to search for genres/categories or specific stations
        OutlinedTextField(
            value = genreSearchQuery,
            onValueChange = { genreSearchQuery = it },
            placeholder = { 
                Text(
                    "Filter genres...", 
                    color = TextGray, 
                    style = MaterialTheme.typography.bodySmall 
                ) 
            },
            leadingIcon = { 
                Icon(
                    Icons.Default.Search, 
                    contentDescription = null, 
                    tint = TextGray, 
                    modifier = Modifier.size(16.dp) 
                ) 
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextWhite,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sidebar_search_input"),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = DeepBlueBg,
                unfocusedContainerColor = DeepBlueBg
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Categories List (genres)
        val filteredCategories = categories.filter { category ->
            category.contains(genreSearchQuery, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredCategories) { category ->
                val isSelectedCategory = category == selectedCategory
                val isExpanded = expandedCategories[category] ?: false

                // Filter channels for this category
                val categoryChannels = channels.filter { channel ->
                    category == "All" || channel.groupName == category
                }

                val iconToUse = getIconForCategory(category)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelectedCategory) DeepBlueBg else Color.Transparent)
                ) {
                    // Genre Drawer Header Accordion
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCategorySelect(category)
                                expandedCategories[category] = !isExpanded
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("genre_header_${category.replace(" ", "_")}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = iconToUse,
                                contentDescription = category,
                                tint = if (isSelectedCategory) AccentCyan else TextGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelectedCategory) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelectedCategory) AccentCyan else TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Total channels count badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = categoryChannels.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Accordion",
                                tint = TextGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Channels sub-list under genre header (Accordion body)
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, bottom = 6.dp)
                                .background(Color.Black.copy(alpha = 0.2f))
                        ) {
                            if (categoryChannels.isEmpty()) {
                                Text(
                                    text = "No channels inside",
                                    color = TextGray,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            } else {
                                categoryChannels.forEach { channel ->
                                    val isCurrentChannel = selectedChannel?.streamUrl == channel.streamUrl
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCurrentChannel) PrimaryBlue.copy(alpha = 0.35f) else Color.Transparent)
                                            .clickable { onChannelSelect(channel) }
                                            .padding(vertical = 8.dp, horizontal = 10.dp)
                                            .testTag("sidebar_channel_${channel.name.replace(" ", "_")}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mini Logo / Icon
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(DeepBlueBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (channel.logoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = channel.logoUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Tv,
                                                    contentDescription = null,
                                                    tint = if (isCurrentChannel) AccentCyan else TextGray,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                color = if (isCurrentChannel) AccentCyan else TextWhite,
                                                fontWeight = if (isCurrentChannel) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (channel.isFavorite) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Starred",
                                                tint = Color.Red,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns dynamic beautiful icon matching category names to prevent AI slop.
 */
private fun getIconForCategory(name: String): ImageVector {
    val cleanName = name.lowercase()
    return when {
        cleanName == "all" -> Icons.Default.GridOn
        cleanName.contains("sport") -> Icons.Default.SportsBasketball
        cleanName.contains("news") || cleanName.contains("info") -> Icons.Default.Newspaper
        cleanName.contains("movie") || cleanName.contains("cinema") -> Icons.Default.Movie
        cleanName.contains("doc") || cleanName.contains("history") || cleanName.contains("science") -> Icons.Default.Science
        cleanName.contains("music") || cleanName.contains("song") -> Icons.Default.MusicNote
        cleanName.contains("kid") || cleanName.contains("cartoon") || cleanName.contains("child") -> Icons.Default.ChildCare
        cleanName.contains("entertainment") || cleanName.contains("live") || cleanName.contains("tv") || cleanName.contains("general") -> Icons.Default.LiveTv
        else -> Icons.Default.FolderOpen
    }
}
