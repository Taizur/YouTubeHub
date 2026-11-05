package com.youtubehub.browser;

import com.youtubehub.model.Channel;
import com.youtubehub.model.Playlist;
import com.youtubehub.model.Tier;
import com.youtubehub.model.Video;
import com.youtubehub.service.ChannelService;
import com.youtubehub.service.MusicService;
import com.youtubehub.service.VideoCache;
import com.youtubehub.storage.MusicRepository;

import java.util.List;

// What the extension's popup can do: add the page you're on as a song or a channel.
// Same services the "Add from clipboard" buttons use, just with the link coming from Opera instead.
public class AddFromBrowser {
    private final ChannelService channelService;
    private final MusicService musicService;
    private final MusicRepository musicRepository;
    private final VideoCache videoCache;
    private Runnable onAdded = () -> {};

    public AddFromBrowser(ChannelService channelService, MusicService musicService,
                          MusicRepository musicRepository, VideoCache videoCache) {
        this.channelService = channelService;
        this.musicService = musicService;
        this.musicRepository = musicRepository;
        this.videoCache = videoCache;
    }

    // The UI passes code that reloads its lists. Called from a server thread, so the UI wraps it in Platform.runLater.
    public void setOnAdded(Runnable onAdded) {
        this.onAdded = onAdded;
    }

    public List<Playlist> getPlaylists() throws Exception {
        return musicRepository.getPlaylists();
    }

    // Returns a message for the popup. Throws IllegalArgumentException for "not a video link" and the like.
    public String addSong(String url, Integer playlistId) throws Exception {
        Video song = musicService.addSongFromLink(url, playlistId); // 1 quota unit
        onAdded.run();
        String playlistName = null;
        for (Playlist playlist : musicRepository.getPlaylists()) {
            if (playlistId != null && playlist.id() == playlistId) {
                playlistName = playlist.name();
            }
        }
        return "Added " + song.getVideoTitle() + (playlistName == null ? "" : " to " + playlistName);
    }

    public String addChannel(String url, int priority, boolean podcast) throws Exception {
        Channel channel = channelService.addChannelFromLink(url, priority, podcast); // 1 quota unit
        videoCache.refreshChannel(channel); // fetch its videos now so it shows up in the feed straight away
        onAdded.run();
        return "Added " + channel.getChannelTitle() + " (" + Tier.fromLevel(channel.getPriority())
                + (podcast ? ", podcast)" : ")");
    }
}
