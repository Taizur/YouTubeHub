package com.youtubehub.ui;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.config.AppConfig;
import com.youtubehub.model.ScoredVideo;
import com.youtubehub.model.Video;
import com.youtubehub.service.ChannelService;
import com.youtubehub.service.PodcastService;
import com.youtubehub.service.SubscriptionService;
import com.youtubehub.service.VideoCache;
import com.youtubehub.service.VideoOpener;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.WatchedRepository;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The real app (Main is just the test bench).
// JavaFX calls start() once, after it has set itself up, and hands you the main window (the "Stage").
//
// Layout:
//   top:    [Refresh] [Show watched]  status text
//   center: tabs — Podcasts | Subscriptions | Channels
//
// Only Refresh (on launch, every hour, or the button) talks to YouTube. Everything else
// rebuilds the lists from the VideoCache, which is free.
public class YouTubeHubApp extends Application {

    // Same settings Main uses for now. Later these become user settings in the UI.
    private static final int PODCASTS_PER_CHANNEL = 5;
    private static final int SUBSCRIPTIONS_PER_CHANNEL = 3;
    private static final double HOURS_PER_PRIORITY_POINT = 24;

    private ChannelRepository channelRepository;
    private WatchedRepository watchedRepository;
    private VideoCache videoCache;
    private PodcastService podcastService;
    private SubscriptionService subscriptionService;
    private ChannelService channelService;
    private VideoOpener videoOpener;

    // The rows read this to decide which videos get the "✓ Watched" look.
    // It's one Set that gets refilled, so every row always sees the latest version.
    private final Set<String> watchedIds = new HashSet<>();

    private final ListView<Video> podcastList = new ListView<>();
    private final ListView<Video> subscriptionList = new ListView<>();
    private final Button refreshButton = new Button("Refresh");
    private final CheckBox showWatched = new CheckBox("Show watched");
    private final Label statusLabel = new Label();

    @Override
    public void start(Stage stage) {
        try {
            setUpServices();
        } catch (Exception e) {
            showError("Couldn't start YouTubeHub", e);
            return;
        }

        setUpVideoList(podcastList);
        setUpVideoList(subscriptionList);

        // Toolbar along the top
        refreshButton.setOnAction(event -> refreshFromYouTube());
        showWatched.setOnAction(event -> rebuildFeeds()); // toggling only re-filters, no API call
        HBox toolbar = new HBox(12, refreshButton, showWatched, statusLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));

        // Tabs. setClosable(false) hides the little "x" on each tab.
        ChannelsPane channelsPane = new ChannelsPane(channelRepository, channelService, videoCache, this::rebuildFeeds);
        Tab podcastTab = new Tab("Podcasts", podcastList);
        Tab subscriptionTab = new Tab("Subscriptions", subscriptionList);
        Tab channelsTab = new Tab("Channels", channelsPane);
        podcastTab.setClosable(false);
        subscriptionTab.setClosable(false);
        channelsTab.setClosable(false);
        TabPane tabs = new TabPane(podcastTab, subscriptionTab, channelsTab);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(tabs);

        stage.setTitle("YouTubeHub");
        stage.setScene(new Scene(root, 1000, 700));
        stage.show();

        // Refresh now, then again every hour for as long as the app is open.
        // A Timeline is JavaFX's timer: one KeyFrame "after 1 hour, do this", repeated forever.
        refreshFromYouTube();
        Timeline hourly = new Timeline(new KeyFrame(javafx.util.Duration.hours(1), event -> refreshFromYouTube()));
        hourly.setCycleCount(Animation.INDEFINITE);
        hourly.play();
    }

    // Config, database tables, and the services that build the feeds.
    private void setUpServices() throws Exception {
        AppConfig config = new AppConfig();
        config.load();

        YouTubeClient client = new YouTubeClient(config.getApiKey());

        channelRepository = new ChannelRepository();
        channelRepository.initialize();
        watchedRepository = new WatchedRepository();
        watchedRepository.initialize();

        videoCache = new VideoCache(client);
        podcastService = new PodcastService(channelRepository, watchedRepository, videoCache);
        subscriptionService = new SubscriptionService(channelRepository, watchedRepository, videoCache, HOURS_PER_PRIORITY_POINT);
        channelService = new ChannelService(channelRepository, client);
        videoOpener = new VideoOpener(config.getOperaPath(), watchedRepository);
    }

    // Rows with thumbnails, double-click to open, right-click menu.
    private void setUpVideoList(ListView<Video> list) {
        list.setCellFactory(view -> new VideoListCell(watchedIds));

        // Double-click: open in Opera GX (which also marks it watched)
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelected(list);
            }
        });

        // Right-click menu. Each item acts on whichever row is selected (right-clicking a row selects it).
        MenuItem open = new MenuItem("Open in Opera GX");
        MenuItem markWatched = new MenuItem("Mark watched");
        MenuItem markUnwatched = new MenuItem("Mark unwatched");
        open.setOnAction(event -> openSelected(list));
        markWatched.setOnAction(event -> setSelectedWatched(list, true));
        markUnwatched.setOnAction(event -> setSelectedWatched(list, false));
        list.setContextMenu(new ContextMenu(open, markWatched, markUnwatched));
    }

    private void openSelected(ListView<Video> list) {
        Video video = list.getSelectionModel().getSelectedItem();
        if (video == null) {
            return;
        }
        try {
            videoOpener.open(video.getVideoId());
            rebuildFeeds();
        } catch (Exception e) {
            showError("Couldn't open the video", e);
        }
    }

    private void setSelectedWatched(ListView<Video> list, boolean watched) {
        Video video = list.getSelectionModel().getSelectedItem();
        if (video == null) {
            return;
        }
        try {
            if (watched) {
                watchedRepository.markWatched(video.getVideoId());
            } else {
                watchedRepository.markUnwatched(video.getVideoId());
            }
            rebuildFeeds();
        } catch (Exception e) {
            showError("Couldn't update watched status", e);
        }
    }

    // Fetches every channel from YouTube on a background thread, then rebuilds the lists.
    // (The window would freeze if this ran on the screen's own thread while waiting on the internet.
    // JavaFX only lets you change the screen from its own thread, so results come back via Platform.runLater.)
    private void refreshFromYouTube() {
        if (refreshButton.isDisabled()) {
            return; // already refreshing
        }
        refreshButton.setDisable(true);
        statusLabel.setText("Refreshing...");

        Thread worker = new Thread(() -> {
            try {
                videoCache.refreshAll(channelRepository.getAllChannels());
                Platform.runLater(() -> {
                    rebuildFeeds();
                    statusLabel.setText("Updated at " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Refresh failed");
                    showError("Couldn't refresh from YouTube", e);
                });
            } finally {
                Platform.runLater(() -> refreshButton.setDisable(false));
            }
        });
        worker.setDaemon(true); // don't keep the app running after you close the window
        worker.start();
    }

    // Rebuilds both lists from the cache. Fast and free: no API calls.
    // Runs after a refresh, after opening/marking a video, toggling "Show watched", or editing channels.
    private void rebuildFeeds() {
        try {
            watchedIds.clear();
            watchedIds.addAll(watchedRepository.getWatchedIds());

            boolean hideWatched = !showWatched.isSelected();
            List<Video> podcasts = podcastService.getPodcastFeed(PODCASTS_PER_CHANNEL, hideWatched);

            List<Video> subscriptions = new ArrayList<>();
            for (ScoredVideo scored : subscriptionService.getSubscriptionFeed(SUBSCRIPTIONS_PER_CHANNEL, hideWatched)) {
                subscriptions.add(scored.video()); // already in ranked order
            }

            podcastList.getItems().setAll(podcasts);
            subscriptionList.getItems().setAll(subscriptions);
            podcastList.refresh(); // redraw rows so watched marks update
            subscriptionList.refresh();
        } catch (Exception e) {
            showError("Couldn't build the feeds", e);
        }
    }

    // A popup with the error message, so problems don't just disappear silently.
    private static void showError(String title, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
