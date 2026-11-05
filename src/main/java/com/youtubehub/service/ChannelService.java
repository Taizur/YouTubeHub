package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.api.YouTubeLink;
import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;
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
        return save(channel, priority, podcast);
    }

    // Adds a channel from any copied YouTube link — no need to find the ID yourself.
    //   youtube.com/@joerogan        -> looks up the handle
    //   youtube.com/channel/UC...    -> uses the ID in the link
    //   youtube.com/watch?v=... etc  -> adds the channel that uploaded that video
    // The UI's "Add from clipboard" button will call this with whatever you copied.
    public Channel addChannelFromLink(String text, int priority, boolean podcast)
            throws IOException, InterruptedException, SQLException {
        YouTubeLink link = YouTubeLink.parse(text);
        if (link == null) {
            throw new IllegalArgumentException("That doesn't look like a YouTube channel or video link: " + text);
        }

        Channel channel;
        switch (link.type()) {
            case CHANNEL_ID -> channel = client.getChannel(link.value());
            case HANDLE -> channel = client.getChannelByHandle(link.value());
            case VIDEO_ID -> {
                Video video = client.getVideo(link.value());
                if (video == null) {
                    throw new IllegalArgumentException("No YouTube video found for: " + text);
                }
                channel = client.getChannel(video.getChannelId());
            }
            default -> channel = null;
        }

        if (channel == null) {
            throw new IllegalArgumentException("No YouTube channel found for: " + text);
        }
        return save(channel, priority, podcast);
    }

    // Applies your settings and saves the channel to the database.
    private Channel save(Channel channel, int priority, boolean podcast) throws SQLException {
        channel.setPriority(priority);
        channel.setPodcast(podcast);
        channelRepository.addChannel(channel);
        return channel;
    }
}
