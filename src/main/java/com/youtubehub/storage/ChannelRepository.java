package com.youtubehub.storage;

import com.youtubehub.model.Channel;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Saves and loads your channel list in a local SQLite database file (youtubehub.db in the project folder).
public class ChannelRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:youtubehub.db";

    // Opens a connection to the database file. SQLite creates the file automatically if it doesn't exist.
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // Creates the channels table the first time the app runs. Safe to call every startup.
    public void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS channels (
                    channel_id          TEXT PRIMARY KEY,
                    channel_title       TEXT NOT NULL,
                    uploads_playlist_id TEXT NOT NULL,
                    priority            INTEGER NOT NULL,
                    podcast             INTEGER NOT NULL
                );
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);

            // One-time tidy-up from the old 1–10 priorities to tier levels 1–4 (see model/Tier).
            // Old 5 -> Favorite (4); anything already 1–4 is left alone. Harmless to run every startup.
            statement.execute("UPDATE channels SET priority = 4 WHERE priority > 4;");
            statement.execute("UPDATE channels SET priority = 1 WHERE priority < 1;");
        }
    }

    // Adds a channel. If it's already saved, its info is overwritten instead of creating a duplicate.
    public void addChannel(Channel channel) throws SQLException {
        String sql = """
                INSERT INTO channels (channel_id, channel_title, uploads_playlist_id, priority, podcast)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(channel_id) DO UPDATE SET
                    channel_title = excluded.channel_title,
                    uploads_playlist_id = excluded.uploads_playlist_id,
                    priority = excluded.priority,
                    podcast = excluded.podcast;
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channel.getChannelId());
            statement.setString(2, channel.getChannelTitle());
            statement.setString(3, channel.getUploadsPlaylistId());
            statement.setInt(4, channel.getPriority());
            statement.setBoolean(5, channel.isPodcast());
            statement.executeUpdate();
        }
    }

    // Returns every saved channel as a list of Channel objects.
    public List<Channel> getAllChannels() throws SQLException {
        String sql = "SELECT channel_id, channel_title, uploads_playlist_id, priority, podcast FROM channels;";
        List<Channel> channels = new ArrayList<>();

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {

            while (results.next()) {
                channels.add(new Channel(
                        results.getString("channel_id"),
                        results.getString("channel_title"),
                        results.getString("uploads_playlist_id"),
                        results.getInt("priority"),
                        results.getBoolean("podcast")
                ));
            }
        }
        return channels;
    }

    // Removes a channel by its ID.
    public void removeChannel(String channelId) throws SQLException {
        String sql = "DELETE FROM channels WHERE channel_id = ?;";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channelId);
            statement.executeUpdate();
        }
    }

    // Changes a channel's priority.
    public void updatePriority(String channelId, int priority) throws SQLException {
        String sql = "UPDATE channels SET priority = ? WHERE channel_id = ?;";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, priority);
            statement.setString(2, channelId);
            statement.executeUpdate();
        }
    }

    // Marks a channel as a podcast or not.
    public void updatePodcast(String channelId, boolean podcast) throws SQLException {
        String sql = "UPDATE channels SET podcast = ? WHERE channel_id = ?;";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, podcast);
            statement.setString(2, channelId);
            statement.executeUpdate();
        }
    }
}
