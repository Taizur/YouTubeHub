package com.youtubehub.ui;

import com.youtubehub.model.Channel;
import com.youtubehub.service.ChannelService;
import com.youtubehub.service.VideoCache;
import com.youtubehub.storage.ChannelRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;

// The Channels tab: add a channel from a copied link, change priority / podcast, remove.
//
//   top:    [priority] [Podcast] [Add from clipboard]  message
//   center: list of your channels
//   bottom: edit the selected one: [priority] [Podcast] [Save] [Remove]
public class ChannelsPane extends BorderPane {
    private static final int MIN_PRIORITY = 1;
    private static final int MAX_PRIORITY = 10;

    private final ChannelRepository channelRepository;
    private final ChannelService channelService;
    private final VideoCache videoCache;
    private final Runnable onChannelsChanged; // tells the app to rebuild the feeds

    private final ListView<Channel> channelList = new ListView<>();
    private final Label message = new Label();

    private final Spinner<Integer> newPriority = new Spinner<>(MIN_PRIORITY, MAX_PRIORITY, 3);
    private final CheckBox newPodcast = new CheckBox("Podcast");
    private final Button addButton = new Button("Add from clipboard");

    private final Label selectedName = new Label("Select a channel");
    private final Spinner<Integer> editPriority = new Spinner<>(MIN_PRIORITY, MAX_PRIORITY, 3);
    private final CheckBox editPodcast = new CheckBox("Podcast");
    private final Button saveButton = new Button("Save");
    private final Button removeButton = new Button("Remove");
    private final HBox editBar;

    // A Runnable is just "a piece of code to run later", passed around like a value.
    public ChannelsPane(ChannelRepository channelRepository, ChannelService channelService,
                        VideoCache videoCache, Runnable onChannelsChanged) {
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.videoCache = videoCache;
        this.onChannelsChanged = onChannelsChanged;

        // --- top: add ---
        Label hint = new Label("Copy a channel or video link (or use the Opera extension), then:");
        hint.getStyleClass().add("muted");
        message.getStyleClass().add("message");
        addButton.getStyleClass().add("accent");
        newPriority.setPrefWidth(80);
        HBox addBar = new HBox(10, hint, new Label("Priority"), newPriority, newPodcast, addButton, message);
        addBar.getStyleClass().add("pane-bar");
        addBar.setAlignment(Pos.CENTER_LEFT);
        addButton.setOnAction(event -> addFromClipboard());

        // --- center: list ---
        // Each row: channel name in bold, "Priority 5" under it, and a purple "Podcast" badge if it is one.
        channelList.setCellFactory(view -> new ListCell<>() {
            private final Label name = new Label();
            private final Label details = new Label();
            private final Label podcastBadge = new Label("Podcast");
            private final HBox row;

            {   // runs when each row is created (like a constructor for this unnamed class)
                name.getStyleClass().add("video-title");
                details.getStyleClass().add("video-meta");
                podcastBadge.getStyleClass().addAll("badge", "podcast");
                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                row = new HBox(10, new VBox(3, name, details), gap, podcastBadge);
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Channel channel, boolean empty) {
                super.updateItem(channel, empty);
                setText(null);
                if (empty || channel == null) {
                    setGraphic(null);
                } else {
                    name.setText(channel.getChannelTitle());
                    details.setText("Priority " + channel.getPriority());
                    podcastBadge.setVisible(channel.isPodcast());
                    setGraphic(row);
                }
            }
        });
        channelList.setPlaceholder(new Label("No channels yet. Add one above."));

        // --- bottom: edit selected (in a rounded "card") ---
        selectedName.getStyleClass().add("video-title");
        editPriority.setPrefWidth(80);
        Region editGap = new Region();
        HBox.setHgrow(editGap, Priority.ALWAYS);
        editBar = new HBox(10, selectedName, editGap, new Label("Priority"), editPriority, editPodcast, saveButton, removeButton);
        editBar.getStyleClass().add("card");
        editBar.setAlignment(Pos.CENTER_LEFT);
        editBar.setDisable(true);
        BorderPane.setMargin(editBar, new Insets(0, 16, 16, 16));
        saveButton.setOnAction(event -> saveSelected());
        removeButton.setOnAction(event -> removeSelected());

        // When the selected row changes, copy that channel's settings into the edit controls.
        // (A "listener" is code that runs every time a value changes.)
        channelList.getSelectionModel().selectedItemProperty().addListener((obs, oldChannel, channel) -> {
            editBar.setDisable(channel == null);
            if (channel != null) {
                selectedName.setText(channel.getChannelTitle());
                editPriority.getValueFactory().setValue(clampPriority(channel.getPriority()));
                editPodcast.setSelected(channel.isPodcast());
            } else {
                selectedName.setText("Select a channel");
            }
        });

        setTop(addBar);
        setCenter(channelList);
        setBottom(editBar);

        reloadChannels();
    }

    // Reads the channel list from the database again (highest priority first, then by name).
    public void reloadChannels() {
        try {
            List<Channel> channels = channelRepository.getAllChannels();
            channels.sort(Comparator.comparingInt(Channel::getPriority).reversed()
                    .thenComparing(Channel::getChannelTitle, String.CASE_INSENSITIVE_ORDER));
            channelList.getItems().setAll(channels);
        } catch (Exception e) {
            message.setText("Couldn't load channels: " + e.getMessage());
        }
    }

    private void addFromClipboard() {
        // The clipboard can only be read on the screen's own thread, so read it first
        String copied = Clipboard.getSystemClipboard().getString();
        if (copied == null || copied.isBlank()) {
            message.setText("Clipboard is empty — copy a YouTube link first.");
            return;
        }

        int priority = newPriority.getValue();
        boolean podcast = newPodcast.isSelected();
        addButton.setDisable(true);
        message.setText("Adding...");

        // Looking the channel up and fetching its videos uses the internet, so do it on a background thread
        Thread worker = new Thread(() -> {
            try {
                Channel channel = channelService.addChannelFromLink(copied, priority, podcast);
                videoCache.refreshChannel(channel);
                Platform.runLater(() -> {
                    message.setText("Added " + channel.getChannelTitle());
                    reloadChannels();
                    onChannelsChanged.run();
                });
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> message.setText(e.getMessage())); // e.g. "doesn't look like a YouTube link"
            } catch (Exception e) {
                Platform.runLater(() -> message.setText("Couldn't add channel: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> addButton.setDisable(false));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    // Saves priority/podcast for the selected channel. No API call: the feeds rebuild from the cache.
    private void saveSelected() {
        Channel channel = channelList.getSelectionModel().getSelectedItem();
        if (channel == null) {
            return;
        }
        try {
            channelRepository.updatePriority(channel.getChannelId(), editPriority.getValue());
            channelRepository.updatePodcast(channel.getChannelId(), editPodcast.isSelected());
            message.setText("Saved " + channel.getChannelTitle());
            reloadChannels();
            onChannelsChanged.run();
        } catch (Exception e) {
            message.setText("Couldn't save: " + e.getMessage());
        }
    }

    private void removeSelected() {
        Channel channel = channelList.getSelectionModel().getSelectedItem();
        if (channel == null) {
            return;
        }

        // Ask first, since this can't be undone (you'd have to add it again)
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + channel.getChannelTitle() + " from YouTubeHub?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        Theme.apply(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            channelRepository.removeChannel(channel.getChannelId());
            message.setText("Removed " + channel.getChannelTitle());
            reloadChannels();
            onChannelsChanged.run();
        } catch (Exception e) {
            message.setText("Couldn't remove: " + e.getMessage());
        }
    }

    // Keeps old priorities (e.g. 0) inside the spinner's range
    private static int clampPriority(int priority) {
        return Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, priority));
    }
}
