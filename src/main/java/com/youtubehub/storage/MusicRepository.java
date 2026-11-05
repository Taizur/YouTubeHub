package com.youtubehub.storage;

import com.youtubehub.model.Playlist;
import com.youtubehub.model.Video;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Your music library, in three tables (all in youtubehub.db):
//
//   songs          every song you've added. This IS the master playlist ("All songs").
//   playlists      just the names: "Metal", "Electronic", ...
//   playlist_songs one row per "this song is in this playlist". A song in Metal AND Rock = two rows.
//
// Removing a song from a playlist only deletes its playlist_songs row; the song stays in your library.
// Songs are stored as Video objects so the app can reuse the same list rows and thumbnails.
public class MusicRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:youtubehub.db";

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // Creates the three tables the first time the app runs. Safe to call every startup.
    public void initialize() throws SQLException {
        String songs = """
                CREATE TABLE IF NOT EXISTS songs (
                    video_id      TEXT PRIMARY KEY,
                    title         TEXT NOT NULL,
                    channel_id    TEXT NOT NULL,
                    channel_title TEXT NOT NULL,
                    published_at  TEXT NOT NULL,
                    thumbnail_url TEXT NOT NULL,
                    added_at      TEXT NOT NULL
                );
                """;
        // AUTOINCREMENT = the database picks the next id number for you (1, 2, 3, ...)
        String playlists = """
                CREATE TABLE IF NOT EXISTS playlists (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                );
                """;
        // The primary key is the PAIR, so the same song can't be in the same playlist twice
        String playlistSongs = """
                CREATE TABLE IF NOT EXISTS playlist_songs (
                    playlist_id INTEGER NOT NULL,
                    video_id    TEXT NOT NULL,
                    added_at    TEXT NOT NULL,
                    PRIMARY KEY (playlist_id, video_id)
                );
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(songs);
            statement.execute(playlists);
            statement.execute(playlistSongs);
        }
    }

    // ---------- songs ----------

    // Adds a song to your library. Adding one that's already there just updates its info.
    public void addSong(Video song) throws SQLException {
        String sql = """
                INSERT INTO songs (video_id, title, channel_id, channel_title, published_at, thumbnail_url, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(video_id) DO UPDATE SET
                    title = excluded.title,
                    channel_title = excluded.channel_title,
                    thumbnail_url = excluded.thumbnail_url;
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, song.getVideoId());
            statement.setString(2, song.getVideoTitle());
            statement.setString(3, song.getChannelId());
            statement.setString(4, song.getChannelTitle());
            statement.setString(5, song.getPublishedAt());
            statement.setString(6, song.getThumbnailUrl());
            statement.setString(7, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    // Deletes a song from your library AND from every playlist it was in.
    public void removeSong(String videoId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement fromPlaylists = connection.prepareStatement("DELETE FROM playlist_songs WHERE video_id = ?;");
             PreparedStatement fromLibrary = connection.prepareStatement("DELETE FROM songs WHERE video_id = ?;")) {
            fromPlaylists.setString(1, videoId);
            fromPlaylists.executeUpdate();
            fromLibrary.setString(1, videoId);
            fromLibrary.executeUpdate();
        }
    }

    // Every song in your library, most recently added first. This is the master playlist.
    public List<Video> getAllSongs() throws SQLException {
        String sql = """
                SELECT video_id, title, channel_id, channel_title, published_at, thumbnail_url
                FROM songs
                ORDER BY added_at DESC;
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {
            return readSongs(results);
        }
    }

    // ---------- playlists ----------

    // Makes a new empty playlist. Throws SQLException if you already have one with that name (UNIQUE).
    public void createPlaylist(String name) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO playlists (name) VALUES (?);")) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
    }

    public void renamePlaylist(int playlistId, String newName) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE playlists SET name = ? WHERE id = ?;")) {
            statement.setString(1, newName);
            statement.setInt(2, playlistId);
            statement.executeUpdate();
        }
    }

    // Deletes the playlist and its links. The songs themselves stay in your library.
    public void deletePlaylist(int playlistId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement links = connection.prepareStatement("DELETE FROM playlist_songs WHERE playlist_id = ?;");
             PreparedStatement playlist = connection.prepareStatement("DELETE FROM playlists WHERE id = ?;")) {
            links.setInt(1, playlistId);
            links.executeUpdate();
            playlist.setInt(1, playlistId);
            playlist.executeUpdate();
        }
    }

    // All your playlists, alphabetical.
    public List<Playlist> getPlaylists() throws SQLException {
        List<Playlist> playlists = new ArrayList<>();

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT id, name FROM playlists ORDER BY name COLLATE NOCASE;")) {
            while (results.next()) {
                playlists.add(new Playlist(results.getInt("id"), results.getString("name")));
            }
        }
        return playlists;
    }

    // ---------- songs <-> playlists ----------

    // Puts a song in a playlist. Doing it twice does nothing the second time.
    public void addSongToPlaylist(int playlistId, String videoId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO playlist_songs (playlist_id, video_id, added_at) VALUES (?, ?, ?);";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playlistId);
            statement.setString(2, videoId);
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    public void removeSongFromPlaylist(int playlistId, String videoId) throws SQLException {
        String sql = "DELETE FROM playlist_songs WHERE playlist_id = ? AND video_id = ?;";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playlistId);
            statement.setString(2, videoId);
            statement.executeUpdate();
        }
    }

    // The songs in one playlist, most recently added to it first.
    // JOIN = look up each playlist_songs row's matching row in songs, like matching an item ID to its name.
    public List<Video> getSongsInPlaylist(int playlistId) throws SQLException {
        String sql = """
                SELECT s.video_id, s.title, s.channel_id, s.channel_title, s.published_at, s.thumbnail_url
                FROM playlist_songs ps
                JOIN songs s ON s.video_id = ps.video_id
                WHERE ps.playlist_id = ?
                ORDER BY ps.added_at DESC;
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playlistId);
            try (ResultSet results = statement.executeQuery()) {
                return readSongs(results);
            }
        }
    }

    // Which playlists a song is in (their ids). The UI uses this to tick the right boxes.
    public Set<Integer> getPlaylistIdsForSong(String videoId) throws SQLException {
        Set<Integer> ids = new HashSet<>();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT playlist_id FROM playlist_songs WHERE video_id = ?;")) {
            statement.setString(1, videoId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    ids.add(results.getInt("playlist_id"));
                }
            }
        }
        return ids;
    }

    // Turns rows from the songs table into Video objects.
    private static List<Video> readSongs(ResultSet results) throws SQLException {
        List<Video> songs = new ArrayList<>();
        while (results.next()) {
            songs.add(new Video(
                    results.getString("video_id"),
                    results.getString("title"),
                    results.getString("channel_id"),
                    results.getString("channel_title"),
                    results.getString("published_at"),
                    results.getString("thumbnail_url")
            ));
        }
        return songs;
    }
}
