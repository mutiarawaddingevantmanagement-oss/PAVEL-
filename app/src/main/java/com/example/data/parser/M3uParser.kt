package com.example.data.parser

import com.example.data.db.ChannelEntity
import java.io.BufferedReader
import java.io.StringReader

/**
 * Structured state object representing a fully parsed M3U playlist.
 */
data class ParsedPlaylistState(
    val playlistUrl: String,
    val headerAttributes: Map<String, String> = emptyMap(),
    val totalChannelsCount: Int = 0,
    val groups: List<String> = emptyList(),
    val channels: List<ChannelEntity> = emptyList()
)

object M3uParser {
    
    /**
     * Backward-compatible parse function returning a flat list of channel entities.
     */
    fun parse(m3uContent: String, playlistUrl: String): List<ChannelEntity> {
        return parseToState(m3uContent, playlistUrl).channels
    }

    /**
     * Robust M3U / M3U8 Playlist Parser that extracts tags, custom attributes, EPG info,
     * header parameters, channel groups, URLs, and compiles them into a structured ParsedPlaylistState.
     */
    fun parseToState(m3uContent: String, playlistUrl: String): ParsedPlaylistState {
        val channels = mutableListOf<ChannelEntity>()
        val headerAttributes = mutableMapOf<String, String>()
        val uniqueGroups = mutableSetOf<String>()
        
        val reader = BufferedReader(StringReader(m3uContent))
        var line: String? = reader.readLine()
        
        // Parsed states per channel
        var currentGroupName = "Other"
        var currentLogoUrl = ""
        var currentName = ""
        var orderIndex = 0

        // Parse first line (M3U header) for any global parameters
        if (line != null) {
            val trimmedFirstLine = line.trim()
            if (trimmedFirstLine.startsWith("#EXTM3U")) {
                headerAttributes.putAll(parseAttributes(trimmedFirstLine))
            } else {
                // If the first line doesn't have #EXTM3U, we still want to parse it as part of the loop
                // (some poorly formatted M3U files omit the header)
                reader.reset()
            }
        }

        while (true) {
            line = reader.readLine() ?: break
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            if (trimmedLine.startsWith("#EXTINF:")) {
                val attributes = parseAttributes(trimmedLine)
                
                // Robust metadata extraction
                currentGroupName = attributes["group-title"] ?: "Other"
                currentLogoUrl = attributes["tvg-logo"] ?: attributes["logo"] ?: ""
                
                // Extract Channel Name: Prioritize the portion after the last comma
                val commaIndex = trimmedLine.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmedLine.length - 1) {
                    trimmedLine.substring(commaIndex + 1).trim()
                } else {
                    attributes["tvg-name"] ?: attributes["title"] ?: ""
                }
                
                if (currentName.isEmpty()) {
                    currentName = attributes["tvg-name"] ?: attributes["tvg-id"] ?: "Channel ${orderIndex + 1}"
                }
                
            } else if (trimmedLine.startsWith("#EXTGRP:")) {
                // Handle local group-title override tag
                currentGroupName = trimmedLine.substring("#EXTGRP:".length).trim().takeIf { it.isNotEmpty() } ?: "Other"
                
            } else if (!trimmedLine.startsWith("#")) {
                // Real streaming source URL line
                val nameToUse = currentName.ifEmpty { "Channel ${orderIndex + 1}" }
                
                channels.add(
                    ChannelEntity(
                        streamUrl = trimmedLine,
                        name = nameToUse,
                        logoUrl = currentLogoUrl,
                        groupName = currentGroupName,
                        isFavorite = false,
                        lastWatched = 0,
                        orderIndex = orderIndex++,
                        playlistUrl = playlistUrl
                    )
                )
                
                uniqueGroups.add(currentGroupName)
                
                // Clear state for next stream
                currentGroupName = "Other"
                currentLogoUrl = ""
                currentName = ""
            }
        }

        val sortedGroups = uniqueGroups.toList().sorted()
        return ParsedPlaylistState(
            playlistUrl = playlistUrl,
            headerAttributes = headerAttributes,
            totalChannelsCount = channels.size,
            groups = sortedGroups,
            channels = channels
        )
    }

    /**
     * Parse attribute-value pairs matching either key="value", key='value', or key=value.
     */
    private fun parseAttributes(line: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        // Pattern matches: key = "value" OR key = 'value' OR key = valueOfNoSpaces
        val regex = """([\w\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""".toRegex()
        val matches = regex.findAll(line)
        for (match in matches) {
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].takeIf { it.isNotEmpty() }
                ?: match.groupValues[3].takeIf { it.isNotEmpty() }
                ?: match.groupValues[4]
            if (value.isNotEmpty()) {
                attributes[key] = value
            }
        }
        return attributes
    }
}
