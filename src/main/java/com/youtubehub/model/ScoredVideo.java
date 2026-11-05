package com.youtubehub.model;

// A video plus the numbers used to rank it in the subscription feed.
// "record" = a class that only holds values. Java writes the constructor and getters for you:
// scoredVideo.video(), scoredVideo.priority(), scoredVideo.ageHours(), scoredVideo.effectiveAgeHours()
public record ScoredVideo(Video video, int priority, double ageHours, double effectiveAgeHours) {
}
