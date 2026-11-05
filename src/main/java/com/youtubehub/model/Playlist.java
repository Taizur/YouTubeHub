package com.youtubehub.model;

// One of your music playlists, like "Metal" or "Electronic".
// id is the number the database gave it; name is what you called it.
public record Playlist(int id, String name) {
}
