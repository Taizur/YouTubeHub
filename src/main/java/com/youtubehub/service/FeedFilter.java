package com.youtubehub.service;

import com.youtubehub.model.Video;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Shared by the podcast and subscription feeds.
public class FeedFilter {

    // Goes through videos (already newest first) and keeps the first `limit` of them,
    // skipping any whose ID is in watchedIds when hideWatched is true.
    // Skipping BEFORE counting means a channel still fills up to `limit` after watched ones are hidden.
    //
    // maxAgeDays: videos older than this many days are left out (0 = no limit). Because the list is
    // newest first, the first too-old video means every one after it is too old too, so it stops there.
    public static List<Video> newest(List<Video> videos, int limit, boolean hideWatched, Set<String> watchedIds,
                                     int maxAgeDays) {
        Instant cutoff = maxAgeDays > 0 ? Instant.now().minus(Duration.ofDays(maxAgeDays)) : null;
        List<Video> kept = new ArrayList<>();
        for (Video video : videos) {
            if (kept.size() >= limit) {
                break;
            }
            Instant published = video.getPublishedInstant();
            if (cutoff != null && published != null && published.isBefore(cutoff)) {
                break; // too old, and so is everything after it
            }
            if (hideWatched && watchedIds.contains(video.getVideoId())) {
                continue;
            }
            kept.add(video);
        }
        return kept;
    }
}
