package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.WatchedRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class PodcastService {
    private final ChannelRepository channelRepository;
    private final WatchedRepository watchedRepository;
    private final YouTubeClient client;

    public PodcastService(ChannelRepository channelRepository, WatchedRepository watchedRepository, YouTubeClient client) {
        this.channelRepository = channelRepository;
        this.watchedRepository = watchedRepository;
        this.client = client;
    }

    // videosPerChannel = how many recent episodes to grab from each podcast channel
    // hideWatched = skip episodes you've already watched (the channel still fills up to videosPerChannel)
    public List<Video> getPodcastFeed(int videosPerChannel, boolean hideWatched) throws SQLException, IOException, InterruptedException {
        List<Channel> channels = channelRepository.getAllChannels();
        Set<String> watchedIds = watchedRepository.getWatchedIds();

        List<Video> podcasts = new ArrayList<>();

        // Collect recent videos from every channel marked as a podcast
        for (Channel channel : channels) {
            if (!channel.isPodcast()) {
                continue;
            }

            // Ask for the full batch (50), then pick the newest ones you haven't watched
            List<Video> recent = client.getVideosFromPlaylist(channel.getUploadsPlaylistId(), 50);
            podcasts.addAll(FeedFilter.newest(recent, videosPerChannel, hideWatched, watchedIds));
        }

        // Newest first
        podcasts.sort(Comparator.comparing(Video::getPublishedAt).reversed());
        return podcasts;
    }
}
