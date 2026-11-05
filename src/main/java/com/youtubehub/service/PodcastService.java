package com.youtubehub.service;

import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.WatchedRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Builds the podcast feed from videos already in the VideoCache (no API calls here).
public class PodcastService {
    private final ChannelRepository channelRepository;
    private final WatchedRepository watchedRepository;
    private final VideoCache videoCache;

    public PodcastService(ChannelRepository channelRepository, WatchedRepository watchedRepository, VideoCache videoCache) {
        this.channelRepository = channelRepository;
        this.watchedRepository = watchedRepository;
        this.videoCache = videoCache;
    }

    // videosPerChannel = how many recent episodes to show from each podcast channel
    // hideWatched = skip episodes you've already watched (the channel still fills up to videosPerChannel)
    // maxAgeDays = leave out episodes older than this many days (0 = no limit)
    public List<Video> getPodcastFeed(int videosPerChannel, boolean hideWatched, int maxAgeDays) throws SQLException {
        List<Channel> channels = channelRepository.getAllChannels();
        Set<String> watchedIds = watchedRepository.getWatchedIds();

        List<Video> podcasts = new ArrayList<>();

        // Collect recent videos from every channel marked as a podcast
        for (Channel channel : channels) {
            if (!channel.isPodcast()) {
                continue;
            }

            List<Video> recent = videoCache.getVideos(channel.getChannelId());
            podcasts.addAll(FeedFilter.newest(recent, videosPerChannel, hideWatched, watchedIds, maxAgeDays));
        }

        // Newest first
        podcasts.sort(Comparator.comparing(Video::getPublishedAt).reversed());
        return podcasts;
    }
}
