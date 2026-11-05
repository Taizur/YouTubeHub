package com.youtubehub.service;

import com.youtubehub.storage.WatchedRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

// Opens a video in a new Opera GX tab and marks it watched.
// The UI will call open(videoId) when you click a video.
public class VideoOpener {
    private final String operaPath;
    private final WatchedRepository watchedRepository;

    public VideoOpener(String operaPath, WatchedRepository watchedRepository) {
        this.operaPath = operaPath;
        this.watchedRepository = watchedRepository;
    }

    public void open(String videoId) throws IOException, SQLException {
        openWithoutMarking(videoId);

        // Only reached if the launch didn't throw, so a failed open never counts as watched.
        watchedRepository.markWatched(videoId);
    }

    // Just opens the tab, without touching watched status. Used for music: songs get replayed,
    // so "watched" doesn't mean anything for them.
    public void openWithoutMarking(String videoId) throws IOException {
        if (operaPath == null || !Files.exists(Path.of(operaPath))) {
            throw new IllegalStateException("Opera GX not found. Set opera.path in config.properties (currently: " + operaPath + ")");
        }

        String url = "https://www.youtube.com/watch?v=" + videoId;

        // Same as typing:  "C:/.../opera.exe" https://www.youtube.com/watch?v=...
        // If Opera is already open, it adds a new tab to the open window instead of starting a second copy.
        new ProcessBuilder(operaPath, url).start();
    }
}
