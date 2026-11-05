package com.youtubehub.model;

// The numbers you can change on the Settings tab.
//   podcastsPerChannel      how many recent episodes each podcast channel puts in the Podcasts feed
//   subscriptionsPerChannel how many recent videos each other channel puts in the Subscriptions feed
//   hoursPerPriorityPoint   how much a channel's tier beats recency, in hours per tier step (see SubscriptionService)
//   maxAgeDays              leave out videos older than this many days, in both feeds (0 = no limit)
public record Settings(int podcastsPerChannel, int subscriptionsPerChannel, int hoursPerPriorityPoint, int maxAgeDays) {
    public static final Settings DEFAULTS = new Settings(5, 3, 24, 0);
}
