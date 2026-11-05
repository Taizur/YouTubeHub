package com.youtubehub.service;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.api.YouTubeLink;
import com.youtubehub.model.Video;
import com.youtubehub.storage.MusicRepository;

import java.io.IOException;
import java.sql.SQLException;

// Adding songs. Music is song-based: any video can be a song, no matter which channel it's from.
public class MusicService {
    private final MusicRepository musicRepository;
    private final YouTubeClient client;

    public MusicService(MusicRepository musicRepository, YouTubeClient client) {
        this.musicRepository = musicRepository;
        this.client = client;
    }

    // Adds the video from a copied link to your library (1 quota unit to look up its title/thumbnail).
    // If playlistId isn't null, also puts it in that playlist.
    // Returns the song so the UI can say "Added <title>".
    // (Integer instead of int so it can be null = "no playlist")
    public Video addSongFromLink(String text, Integer playlistId) throws IOException, InterruptedException, SQLException {
        YouTubeLink link = YouTubeLink.parse(text);
        if (link == null) {
            throw new IllegalArgumentException("That doesn't look like a YouTube video link: " + text);
        }
        if (link.type() != YouTubeLink.Type.VIDEO_ID) {
            throw new IllegalArgumentException("That's a channel link. Copy the link of the song's video instead.");
        }

        Video song = client.getVideo(link.value());
        if (song == null) {
            throw new IllegalArgumentException("No YouTube video found for: " + text);
        }

        musicRepository.addSong(song);
        if (playlistId != null) {
            musicRepository.addSongToPlaylist(playlistId, song.getVideoId());
        }
        return song;
    }
}
