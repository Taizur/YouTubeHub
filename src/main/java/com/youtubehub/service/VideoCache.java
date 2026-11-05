package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Keeps the last batch of videos fetched from YouTube, per channel, in memory.
// Only refreshAll / refreshChannel call the API. Building feeds, hiding watched videos,
// changing priorities etc. all read from here for free.
//
// Think of it like your bank: refreshing is the trip to the bank, everything else is
// rearranging what's already in your inventory.
public class VideoCache {
    // How many recent uploads to keep per channel (the API max per request).
    // Streams and Shorts are already removed, so feeds pick from what's left.
    private static final int UPLOADS_PER_CHANNEL = 50;

    private final YouTubeClient client;

    // channel ID -> that channel's recent videos (newest first)
    // "volatile" because a background thread fills it while the screen reads it.
    private volatile Map<String, List<Video>> videosByChannel = new HashMap<>();
    private volatile Instant lastRefreshed;

    public VideoCache(YouTubeClient client) {
        this.client = client;
    }

    // Fetches every channel again (about 2 quota units per channel).
    public void refreshAll(List<Channel> channels) throws IOException, InterruptedException {
        // Fill a brand-new map, then swap it in at the end, so the screen never sees a half-finished one
        Map<String, List<Video>> fresh = new HashMap<>();
        for (Channel channel : channels) {
            fresh.put(channel.getChannelId(), client.getVideosFromPlaylist(channel.getUploadsPlaylistId(), UPLOADS_PER_CHANNEL));
        }
        videosByChannel = fresh;
        lastRefreshed = Instant.now();
    }

    // Fetches just one channel, e.g. right after you add it.
    public void refreshChannel(Channel channel) throws IOException, InterruptedException {
        List<Video> videos = client.getVideosFromPlaylist(channel.getUploadsPlaylistId(), UPLOADS_PER_CHANNEL);
        Map<String, List<Video>> updated = new HashMap<>(videosByChannel);
        updated.put(channel.getChannelId(), videos);
        videosByChannel = updated;
    }

    // The saved videos for a channel, or an empty list if it hasn't been fetched yet.
    public List<Video> getVideos(String channelId) {
        return videosByChannel.getOrDefault(channelId, List.of());
    }

    // When refreshAll last finished, or null if it never has.
    public Instant getLastRefreshed() {
        return lastRefreshed;
    }
}
