package com.youtubehub.model;

public class Channel {
    private String channelId;
    private String channelTitle;
    private String uploadsPlaylistId;
    private int priority;
    private boolean podcast;

    public Channel(String channelId, String channelTitle, String uploadsPlaylistId, int priority, boolean podcast) {
        this.channelId = channelId;
        this.channelTitle = channelTitle;
        this.uploadsPlaylistId = uploadsPlaylistId;
        this.priority = priority;
        this.podcast = podcast;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public String getUploadsPlaylistId() {
        return uploadsPlaylistId;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isPodcast() {
        return podcast;
    }

    // These two can change after a channel is added (from the UI later), so they get setters.
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setPodcast(boolean podcast) {
        this.podcast = podcast;
    }
}
