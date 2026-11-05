package com.youtubehub.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

// Remembers which videos you've watched in YouTubeHub (YouTube's API doesn't share your real watch history).
// Stored in the same youtubehub.db file as your channels, in its own table.
public class WatchedRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:youtubehub.db";

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // Creates the watched_videos table the first time the app runs. Safe to call every startup.
    public void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS watched_videos (
                    video_id   TEXT PRIMARY KEY,
                    watched_at TEXT NOT NULL
                );
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // Marks a video as watched, saving the current time. Marking it again just updates the time.
    public void markWatched(String videoId) throws SQLException {
        String sql = """
                INSERT INTO watched_videos (video_id, watched_at)
                VALUES (?, ?)
                ON CONFLICT(video_id) DO UPDATE SET watched_at = excluded.watched_at;
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, videoId);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    // Marks a video as not watched by removing it from the table.
    public void markUnwatched(String videoId) throws SQLException {
        String sql = "DELETE FROM watched_videos WHERE video_id = ?;";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, videoId);
            statement.executeUpdate();
        }
    }

    // True if the video has been marked watched.
    public boolean isWatched(String videoId) throws SQLException {
        String sql = "SELECT 1 FROM watched_videos WHERE video_id = ?;";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, videoId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next(); // a row came back = it's in the table
            }
        }
    }

    // Every watched video ID at once. The feeds use this so they don't ask the database once per video.
    public Set<String> getWatchedIds() throws SQLException {
        String sql = "SELECT video_id FROM watched_videos;";
        Set<String> ids = new HashSet<>();

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {
            while (results.next()) {
                ids.add(results.getString("video_id"));
            }
        }
        return ids;
    }
}
