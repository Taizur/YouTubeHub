package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.model.Channel;
import com.youtubehub.storage.ChannelRepository;

import java.io.IOException;
import java.sql.SQLException;

// Channel management: the UI's "Add channel" button will call this.
public class ChannelService {
    private final ChannelRepository channelRepository;
    private final YouTubeClient client;

    public ChannelService(ChannelRepository channelRepository, YouTubeClient client) {
        this.channelRepository = channelRepository;
        this.client = client;
    }

    // Looks the channel up on YouTube, applies your settings, and saves it to the database.
    // Adding a channel that's already saved just updates it.
    // Returns the saved Channel so the caller can show its name.
    public Channel addChannel(String channelId, int priority, boolean podcast)
            throws IOException, InterruptedException, SQLException {
        Channel channel = client.getChannel(channelId.trim());
        if (channel == null) {
            throw new IllegalArgumentException("No YouTube channel found with ID: " + channelId);
        }

        channel.setPriority(priority);
        channel.setPodcast(podcast);
        channelRepository.addChannel(channel);
        return channel;
    }
}
