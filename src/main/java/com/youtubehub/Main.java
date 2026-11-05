package com.youtubehub;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.config.AppConfig;
import com.youtubehub.model.ScoredVideo;
import com.youtubehub.model.Video;
import com.youtubehub.service.PodcastService;
import com.youtubehub.service.SubscriptionService;
import com.youtubehub.service.VideoCache;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.WatchedRepository;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            AppConfig appConfig = new AppConfig();
            appConfig.load();

            YouTubeClient client = new YouTubeClient(appConfig.getApiKey());

            ChannelRepository channelRepository = new ChannelRepository();
            channelRepository.initialize();

            WatchedRepository watchedRepository = new WatchedRepository();
            watchedRepository.initialize();

            // Fetch every channel's recent videos from YouTube once; the feeds below read from this
            VideoCache videoCache = new VideoCache(client);
            videoCache.refreshAll(channelRepository.getAllChannels());

            // TEST: build the podcast feed from every saved podcast channel (true = hide watched)
            PodcastService podcastService = new PodcastService(channelRepository, watchedRepository, videoCache);
            List<Video> feed = podcastService.getPodcastFeed(5, true);

            System.out.println("Podcast feed (" + feed.size() + " videos):");
            for (Video video : feed) {
                System.out.println(video.getPublishedAt() + " | " + video.getChannelTitle() + " | " + video.getVideoTitle());
            }

            // TEST: build the subscription feed (24 = each priority point is worth one day of freshness)
            SubscriptionService subscriptionService = new SubscriptionService(channelRepository, watchedRepository, videoCache, 24);
            List<ScoredVideo> subFeed = subscriptionService.getSubscriptionFeed(3, true);

            System.out.println();
            System.out.println("Subscription feed (" + subFeed.size() + " videos):");
            for (ScoredVideo scored : subFeed) {
                System.out.printf("effective %6.1fh | real %6.1fh | p%d | %s | %s%n",
                        scored.effectiveAgeHours(), scored.ageHours(), scored.priority(),
                        scored.video().getChannelTitle(), scored.video().getVideoTitle());
            }
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
