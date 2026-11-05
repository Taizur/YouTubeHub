package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;
import com.youtubehub.storage.ChannelRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PodcastService {
    private final ChannelRepository channelRepository;
    private final YouTubeClient client;

    public PodcastService(ChannelRepository channelRepository, YouTubeClient client) {
        this.channelRepository = channelRepository;
        this.client = client;
    }

    // videosPerChannel = how many recent episodes to grab from each podcast channel
    public List<Video> getPodcastFeed(int videosPerChannel) throws SQLException, IOException, InterruptedException {
        List<Channel> channels = channelRepository.getAllChannels();

        List<Video> podcasts = new ArrayList<>();

        // Collect recent videos from every channel marked as a podcast
        for (Channel channel : channels) {
            if (!channel.isPodcast()) {
                continue;
            }

            List<Video> channelVideos = client.getVideosFromPlaylist(channel.getUploadsPlaylistId(), videosPerChannel);
            podcasts.addAll(channelVideos);
        }

        // Newest first
        podcasts.sort(Comparator.comparing(Video::getPublishedAt).reversed());
        return podcasts;
    }
}
