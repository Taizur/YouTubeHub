package com.youtubehub.model;

import java.time.Instant;

public class Video {
    private String videoId;
    private String videoTitle;
    private String channelId;
    private String channelTitle;
    private String publishedAt;

    public Video(String videoId, String videoTitle, String channelID, String channelTitle, String publishedAt) {
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.channelId = channelID;
        this.channelTitle = channelTitle;
        this.publishedAt = publishedAt;

    }
    public String getVideoId() {
        return videoId;
    }
    public String getVideoTitle() {
        return videoTitle;
    }
    public String getChannelId() {
        return channelId;
    }
    public String getChannelTitle() {
        return channelTitle;
    }
    public String getPublishedAt() {
        return publishedAt;
    }

    // The upload time as an Instant (an exact moment in time) so you can do math with it,
    // like "how many hours ago was this?". Returns null if YouTube didn't give a date
    // (happens with private or deleted videos in a playlist).
    public Instant getPublishedInstant() {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        return Instant.parse(publishedAt);
    }
}
