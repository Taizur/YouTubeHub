package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.model.Channel;
import com.youtubehub.model.ScoredVideo;
import com.youtubehub.model.Video;
import com.youtubehub.storage.ChannelRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Builds the subscription feed: recent uploads from every non-podcast channel,
// ranked by a mix of channel priority and how new the video is.
//
// How the ranking works ("effective age"):
//   Every video starts with its real age in hours. Each priority point then makes it
//   count as if it were hoursPerPriorityPoint hours newer. Lowest effective age = top of the feed.
//
//   effectiveAge = realAgeHours - (priority * hoursPerPriorityPoint)
//
// Example with hoursPerPriorityPoint = 24 (one day per point):
//   priority 5, 3 weeks old (504h)  -> 504 - 120 = 384
//   priority 1, 2 hours old (2h)    ->   2 -  24 = -22   <- this one wins
//
// Bigger hoursPerPriorityPoint = priority matters more. 0 = pure newest-first.
public class SubscriptionService {
    private final ChannelRepository channelRepository;
    private final YouTubeClient client;
    private final double hoursPerPriorityPoint;

    public SubscriptionService(ChannelRepository channelRepository, YouTubeClient client, double hoursPerPriorityPoint) {
        this.channelRepository = channelRepository;
        this.client = client;
        this.hoursPerPriorityPoint = hoursPerPriorityPoint;
    }

    public List<ScoredVideo> getSubscriptionFeed(int videosPerChannel) throws SQLException, IOException, InterruptedException {
        List<Channel> channels = channelRepository.getAllChannels();
        List<ScoredVideo> feed = new ArrayList<>();
        Instant now = Instant.now();

        for (Channel channel : channels) {
            if (channel.isPodcast()) {
                continue; // podcasts have their own feed
            }

            List<Video> channelVideos = client.getVideosFromPlaylist(channel.getUploadsPlaylistId(), videosPerChannel);
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
