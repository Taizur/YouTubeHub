package com.youtubehub.ui;

import com.youtubehub.model.Playlist;
import com.youtubehub.model.Video;
import com.youtubehub.service.MusicService;
import com.youtubehub.service.VideoOpener;
import com.youtubehub.storage.MusicRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// The Music tab.
//
//   left:   "All songs" + your playlists, with [New] [Rename] [Delete] under them
//   top:    [Add song from clipboard]  message
//   center: songs in whatever's selected on the left
//
// Right-click a song: open it, tick/untick which playlists it's in, remove it.
public class MusicPane extends BorderPane {
    // Not a real playlist: stands for "every song in your library". Real playlist ids start at 1.
    private static final Playlist ALL_SONGS = new Playlist(0, "All songs");

    private final MusicRepository musicRepository;
    private final MusicService musicService;
    private final VideoOpener videoOpener;

    private final ListView<Playlist> playlistList = new ListView<>();
    private final ListView<Video> songList = new ListView<>();
    private final Label message = new Label();
    private final Button addButton = new Button("Add song from clipboard");

    public MusicPane(MusicRepository musicRepository, MusicService musicService, VideoOpener videoOpener) {
        this.musicRepository = musicRepository;
        this.musicService = musicService;
        this.videoOpener = videoOpener;

        // --- left: playlists ---
        // Playlist is a record, so ListView would show "Playlist[id=1, name=Metal]". Show just the name.
        playlistList.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Playlist playlist, boolean empty) {
                super.updateItem(playlist, empty);
                setText(empty || playlist == null ? null : playlist.name());
            }
        });
        playlistList.getSelectionModel().selectedItemProperty().addListener((obs, old, playlist) -> showSongs());

        Button newButton = new Button("New");
        Button renameButton = new Button("Rename");
        Button deleteButton = new Button("Delete");
        newButton.setOnAction(event -> newPlaylist());
        renameButton.setOnAction(event -> renamePlaylist());
        deleteButton.setOnAction(event -> deletePlaylist());
        HBox playlistButtons = new HBox(6, newButton, renameButton, deleteButton);

        VBox left = new VBox(6, new Label("Playlists"), playlistList, playlistButtons);
        left.setPadding(new Insets(8));
        left.setPrefWidth(240);

        // --- top: add ---
        Label hint = new Label("Copy a song's video link in Opera (Ctrl+L, Ctrl+C), then:");
        HBox addBar = new HBox(8, hint, addButton, message);
        addBar.setAlignment(Pos.CENTER_LEFT);
        addBar.setPadding(new Insets(8));
        addButton.setOnAction(event -> addFromClipboard());

        // --- center: songs ---
        // An empty set = never show the "watched" look; it doesn't apply to music.
        songList.setCellFactory(view -> new VideoListCell(new HashSet<>()));
        songList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelectedSong();
            }
        });
        ContextMenu songMenu = new ContextMenu();
        songMenu.setOnShowing(event -> buildSongMenu(songMenu)); // rebuilt each time, so the ticks are current
        songList.setContextMenu(songMenu);

        setLeft(left);
        setTop(addBar);
        setCenter(songList);

        reloadPlaylists(ALL_SONGS);
    }

    // Reads the playlists again and selects `toSelect` (matched by id).
    private void reloadPlaylists(Playlist toSelect) {
        try {
            List<Playlist> playlists = musicRepository.getPlaylists();
            playlistList.getItems().setAll(ALL_SONGS);
            playlistList.getItems().addAll(playlists);

            Playlist match = ALL_SONGS;
            for (Playlist playlist : playlistList.getItems()) {
                if (toSelect != null && playlist.id() == toSelect.id()) {
                    match = playlist;
                }
            }
            playlistList.getSelectionModel().select(match);
            showSongs();
        } catch (Exception e) {
            message.setText("Couldn't load playlists: " + e.getMessage());
        }
    }

    // Fills the song list with whatever playlist is selected on the left.
    private void showSongs() {
        Playlist selected = selectedPlaylist();
        try {
            if (selected.id() == ALL_SONGS.id()) {
                songList.getItems().setAll(musicRepository.getAllSongs());
            } else {
                songList.getItems().setAll(musicRepository.getSongsInPlaylist(selected.id()));
            }
        } catch (Exception e) {
            message.setText("Couldn't load songs: " + e.getMessage());
        }
    }

    private Playlist selectedPlaylist() {
        Playlist selected = playlistList.getSelectionModel().getSelectedItem();
        return selected == null ? ALL_SONGS : selected;
    }

    // Adds the copied video to your library, and to the selected playlist if one is selected.
    private void addFromClipboard() {
        String copied = Clipboard.getSystemClipboard().getString(); // must be read on the screen's thread
        if (copied == null || copied.isBlank()) {
            message.setText("Clipboard is empty — copy a YouTube video link first.");
            return;
        }

        Playlist target = selectedPlaylist();
        Integer playlistId = target.id() == ALL_SONGS.id() ? null : target.id();
        addButton.setDisable(true);
        message.setText("Adding...");

        Thread worker = new Thread(() -> {
            try {
                Video song = musicService.addSongFromLink(copied, playlistId);
                Platform.runLater(() -> {
                    message.setText("Added " + song.getVideoTitle() + (playlistId == null ? "" : " to " + target.name()));
                    showSongs();
                });
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> message.setText(e.getMessage()));
            } catch (Exception e) {
                Platform.runLater(() -> message.setText("Couldn't add song: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> addButton.setDisable(false));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    // Right-click menu for the selected song:
    //   Open in Opera GX
    //   Playlists > [x] Metal  [ ] Rock ...   (tick = in that playlist; click to toggle)
    //   Remove from <this playlist>           (only when viewing a playlist)
    //   Delete from library
    private void buildSongMenu(ContextMenu menu) {
        menu.getItems().clear();
        Video song = songList.getSelectionModel().getSelectedItem();
        if (song == null) {
            return;
        }

        MenuItem open = new MenuItem("Open in Opera GX");
        open.setOnAction(event -> openSelectedSong());
        menu.getItems().add(open);

        try {
            Menu playlistsMenu = new Menu("Playlists");
            Set<Integer> inPlaylists = musicRepository.getPlaylistIdsForSong(song.getVideoId());
            for (Playlist playlist : musicRepository.getPlaylists()) {
                CheckMenuItem item = new CheckMenuItem(playlist.name());
                item.setSelected(inPlaylists.contains(playlist.id()));
                item.setOnAction(event -> togglePlaylist(playlist, song, item.isSelected()));
                playlistsMenu.getItems().add(item);
            }
            if (playlistsMenu.getItems().isEmpty()) {
                MenuItem none = new MenuItem("(no playlists yet — click New)");
                none.setDisable(true);
                playlistsMenu.getItems().add(none);
            }
            menu.getItems().add(playlistsMenu);
        } catch (Exception e) {
            message.setText("Couldn't load playlists: " + e.getMessage());
        }

        menu.getItems().add(new SeparatorMenuItem());

        Playlist current = selectedPlaylist();
        if (current.id() != ALL_SONGS.id()) {
            MenuItem removeFromPlaylist = new MenuItem("Remove from " + current.name());
            removeFromPlaylist.setOnAction(event -> togglePlaylist(current, song, false));
            menu.getItems().add(removeFromPlaylist);
        }

        MenuItem delete = new MenuItem("Delete from library");
        delete.setOnAction(event -> deleteSong(song));
        menu.getItems().add(delete);
    }

    private void togglePlaylist(Playlist playlist, Video song, boolean add) {
        try {
            if (add) {
                musicRepository.addSongToPlaylist(playlist.id(), song.getVideoId());
                message.setText("Added to " + playlist.name());
            } else {
                musicRepository.removeSongFromPlaylist(playlist.id(), song.getVideoId());
                message.setText("Removed from " + playlist.name());
            }
            showSongs();
        } catch (Exception e) {
            message.setText("Couldn't update playlist: " + e.getMessage());
        }
    }

    private void openSelectedSong() {
        Video song = songList.getSelectionModel().getSelectedItem();
        if (song == null) {
            return;
        }
        try {
            videoOpener.openWithoutMarking(song.getVideoId());
        } catch (Exception e) {
            message.setText("Couldn't open: " + e.getMessage());
        }
    }

    private void deleteSong(Video song) {
        if (!confirm("Delete \"" + song.getVideoTitle() + "\" from your library and every playlist?")) {
            return;
        }
        try {
            musicRepository.removeSong(song.getVideoId());
            message.setText("Deleted " + song.getVideoTitle());
            showSongs();
        } catch (Exception e) {
            message.setText("Couldn't delete: " + e.getMessage());
        }
    }

    // ---------- playlist buttons ----------

    private void newPlaylist() {
        Optional<String> name = askForName("New playlist", "");
        if (name.isEmpty()) {
            return;
        }
        try {
            musicRepository.createPlaylist(name.get());
            // Select the new one: find it by name after reloading
            for (Playlist playlist : musicRepository.getPlaylists()) {
                if (playlist.name().equals(name.get())) {
                    reloadPlaylists(playlist);
                }
            }
            message.setText("Created " + name.get());
        } catch (Exception e) {
            message.setText("Couldn't create playlist (is that name already used?)");
        }
    }

    private void renamePlaylist() {
        Playlist playlist = selectedPlaylist();
        if (playlist.id() == ALL_SONGS.id()) {
            message.setText("Pick a playlist to rename.");
            return;
        }
        Optional<String> name = askForName("Rename playlist", playlist.name());
        if (name.isEmpty()) {
            return;
        }
        try {
            musicRepository.renamePlaylist(playlist.id(), name.get());
            reloadPlaylists(playlist);
            message.setText("Renamed to " + name.get());
        } catch (Exception e) {
            message.setText("Couldn't rename (is that name already used?)");
        }
    }

    private void deletePlaylist() {
        Playlist playlist = selectedPlaylist();
        if (playlist.id() == ALL_SONGS.id()) {
            message.setText("Pick a playlist to delete.");
            return;
        }
        if (!confirm("Delete the playlist \"" + playlist.name() + "\"? The songs stay in your library.")) {
            return;
        }
        try {
            musicRepository.deletePlaylist(playlist.id());
            reloadPlaylists(ALL_SONGS);
            message.setText("Deleted " + playlist.name());
        } catch (Exception e) {
            message.setText("Couldn't delete playlist: " + e.getMessage());
        }
    }

    // A small popup with a text box. Returns the typed name, or empty if cancelled / left blank.
    private static Optional<String> askForName(String title, String current) {
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText("Name:");
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty());
    }

    private static boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}
