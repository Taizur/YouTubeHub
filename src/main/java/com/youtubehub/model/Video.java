package com.youtubehub.model;

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
}
