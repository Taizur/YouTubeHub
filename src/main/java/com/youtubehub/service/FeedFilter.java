package com.youtubehub.service;

import com.youtubehub.model.Video;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Shared by the podcast and subscription feeds.
public class FeedFilter {

    // Goes through videos (already newest first) and keeps the first `limit` of them,
    // skipping any whose ID is in watchedIds when hideWatched is true.
    // Skipping BEFORE counting means a channel still fills up to `limit` after watched ones are hidden.
    public static List<Video> newest(List<Video> videos, int limit, boolean hideWatched, Set<String> watchedIds) {
        List<Video> kept = new ArrayList<>();
        for (Video video : videos) {
            if (kept.size() >= limit) {
                break;
            }
            if (hideWatched && watchedIds.contains(video.getVideoId())) {
                continue;
            }
            kept.add(video);
        }
        return kept;
    }
}
