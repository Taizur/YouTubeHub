package com.youtubehub.service;

import com.youtubehub.model.Channel;
import com.youtubehub.model.ScoredVideo;
import com.youtubehub.model.Video;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.WatchedRepository;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Builds the subscription feed: recent uploads from every non-podcast channel,
// ranked by a mix of the channel's tier and how new the video is.
//
// How the ranking works ("effective age"):
//   Every video starts with its real age in hours. Each tier level (Desperate 1, Good filler 2,
//   High 3, Favorite 4; see model/Tier, stored as the channel's "priority") then makes it count as if
//   it were hoursPerPriorityPoint hours newer. Lowest effective age = top of the feed.
//
//   effectiveAge = realAgeHours - (tierLevel * hoursPerPriorityPoint)
//
// Example with hoursPerPriorityPoint = 24 (one day per tier step):
//   Favorite (4), 3 weeks old (504h)  -> 504 - 96 = 408
//   Desperate (1), 2 hours old (2h)   ->   2 - 24 = -22   <- this one wins
//
// Bigger hoursPerPriorityPoint = tiers matter more. 0 = pure newest-first.
public class SubscriptionService {
    private final ChannelRepository channelRepository;
    private final WatchedRepository watchedRepository;
    private final VideoCache videoCache;
    private double hoursPerPriorityPoint; // not final: the Settings tab can change it while the app runs

    public SubscriptionService(ChannelRepository channelRepository, WatchedRepository watchedRepository,
                               VideoCache videoCache, double hoursPerPriorityPoint) {
        this.channelRepository = channelRepository;
        this.watchedRepository = watchedRepository;
        this.videoCache = videoCache;
        this.hoursPerPriorityPoint = hoursPerPriorityPoint;
    }

    public void setHoursPerPriorityPoint(double hoursPerPriorityPoint) {
        this.hoursPerPriorityPoint = hoursPerPriorityPoint;
    }

    // Builds the feed from videos already in the VideoCache (no API calls here).
    // hideWatched = skip videos you've already watched (each channel still fills up to videosPerChannel)
    // maxAgeDays = leave out videos older than this many days (0 = no limit)
    public List<ScoredVideo> getSubscriptionFeed(int videosPerChannel, boolean hideWatched, int maxAgeDays) throws SQLException {
        List<Channel> channels = channelRepository.getAllChannels();
        Set<String> watchedIds = watchedRepository.getWatchedIds();
        List<ScoredVideo> feed = new ArrayList<>();
        Instant now = Instant.now();

        for (Channel channel : channels) {
            if (channel.isPodcast()) {
                continue; // podcasts have their own feed
            }

            List<Video> recent = videoCache.getVideos(channel.getChannelId());
            List<Video> channelVideos = FeedFilter.newest(recent, videosPerChannel, hideWatched, watchedIds, maxAgeDays);
            for (Video video : channelVideos) {
                Instant published = video.getPublishedInstant();
                if (published == null) {
                    continue; // no upload date (private/deleted video), can't rank it
                }

                double ageHours = Duration.between(published, now).toMinutes() / 60.0;
                double effectiveAgeHours = ageHours - channel.getPriority() * hoursPerPriorityPoint;
                feed.add(new ScoredVideo(video, channel.getPriority(), ageHours, effectiveAgeHours));
            }
        }

        // Lowest effective age first
        feed.sort(Comparator.comparingDouble(ScoredVideo::effectiveAgeHours));
        return feed;
    }
}
