package com.example

import com.example.data.parser.M3uParser
import org.junit.Assert.*
import org.junit.Test

class M3uParserTest {

    @Test
    fun testParseToState_withStandardPlaylist() {
        val rawPlaylist = """
            #EXTM3U x-tvg-url="http://example.com/epg.xml" url-tvg="http://example.com/epg2.xml"
            #EXTINF:-1 tvg-id="NASA-TV" tvg-name="NASA HD" tvg-logo="https://nasa.gov/logo.png" group-title="Science",NASA Channel Live
            https://nasatv.net/live.m3u8
            #EXTINF:-1 tvg-id="Bloomberg" group-title="News",Bloomberg US
            https://bloomberg.com/live.m3u8
        """.trimIndent()

        val state = M3uParser.parseToState(rawPlaylist, "http://myplaylist.com/list.m3u")

        // Assert general metadata
        assertEquals("http://myplaylist.com/list.m3u", state.playlistUrl)
        assertEquals(2, state.totalChannelsCount)
        assertEquals(2, state.channels.size)
        assertEquals(2, state.groups.size)
        assertTrue(state.groups.contains("Science"))
        assertTrue(state.groups.contains("News"))

        // Assert header attributes
        assertEquals("http://example.com/epg.xml", state.headerAttributes["x-tvg-url"])
        assertEquals("http://example.com/epg2.xml", state.headerAttributes["url-tvg"])

        // Assert channels
        val nasa = state.channels[0]
        assertEquals("NASA Channel Live", nasa.name)
        assertEquals("https://nasatv.net/live.m3u8", nasa.streamUrl)
        assertEquals("https://nasa.gov/logo.png", nasa.logoUrl)
        assertEquals("Science", nasa.groupName)

        val bloomberg = state.channels[1]
        assertEquals("Bloomberg US", bloomberg.name)
        assertEquals("https://bloomberg.com/live.m3u8", bloomberg.streamUrl)
        assertEquals("", bloomberg.logoUrl)
        assertEquals("News", bloomberg.groupName)
    }

    @Test
    fun testParseToState_withExtGrpTagOverride() {
        val rawPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="LocalNews",News Channel
            #EXTGRP:Local Broadcast
            https://localnews.com/live.m3u8
        """.trimIndent()

        val state = M3uParser.parseToState(rawPlaylist, "http://myplaylist.com/list.m3u")

        assertEquals(1, state.totalChannelsCount)
        val channel = state.channels[0]
        assertEquals("News Channel", channel.name)
        assertEquals("Local Broadcast", channel.groupName)
        assertEquals("https://localnews.com/live.m3u8", channel.streamUrl)
    }

    @Test
    fun testParseToState_unquotedAttributes() {
        val rawPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id=MyID tvg-name=MyName tvg-logo=https://example.com/logo.png group-title=Entertainment,Fun TV
            https://funtv.com/stream.m3u8
        """.trimIndent()

        val state = M3uParser.parseToState(rawPlaylist, "http://myplaylist.com/list.m3u")

        assertEquals(1, state.totalChannelsCount)
        val channel = state.channels[0]
        assertEquals("Fun TV", channel.name)
        assertEquals("https://example.com/logo.png", channel.logoUrl)
        assertEquals("Entertainment", channel.groupName)
        assertEquals("https://funtv.com/stream.m3u8", channel.streamUrl)
    }
}
