package com.youtubehub.ui;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.config.AppConfig;
import com.youtubehub.model.ScoredVideo;
import com.youtubehub.model.Video;
import com.youtubehub.service.PodcastService;
import com.youtubehub.service.SubscriptionService;
import com.youtubehub.service.VideoOpener;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.WatchedRepository;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// The real app (Main is just the test bench).
// JavaFX calls start() once, after it has set itself up, and hands you the main window (the "Stage").
public class YouTubeHubApp extends Application {

    // Same settings Main uses for now. Later these become user settings in the UI.
    private static final int PODCASTS_PER_CHANNEL = 5;
    private static final int SUBSCRIPTIONS_PER_CHANNEL = 3;
    private static final double HOURS_PER_PRIORITY_POINT = 24;

    private PodcastService podcastService;
    private SubscriptionService subscriptionService;
    private VideoOpener videoOpener;

    private final ListView<Video> podcastList = new ListView<>();
    private final ListView<Video> subscriptionList = new ListView<>();
    private final Label statusLabel = new Label("Loading...");

    @Override
    public void start(Stage stage) {
        try {
            setUpServices();
        } catch (Exception e) {
            showError("Couldn't start YouTubeHub", e);
            return;
        }

        setUpList(podcastList);
        setUpList(subscriptionList);

        // Tabs along the top. setClosable(false) hides the little "x" on each tab.
        Tab podcastTab = new Tab("Podcasts", podcastList);
        Tab subscriptionTab = new Tab("Subscriptions", subscriptionList);
        podcastTab.setClosable(false);
        subscriptionTab.setClosable(false);
        TabPane tabs = new TabPane(podcastTab, subscriptionTab);

        // BorderPane has five areas: top, bottom, left, right, center. Tabs fill the center, status text sits at the bottom.
        BorderPane root = new BorderPane();
        root.setCenter(tabs);
        root.setBottom(statusLabel);

        stage.setTitle("YouTubeHub");
        stage.setScene(new Scene(root, 1000, 700));
        stage.show();

        loadFeeds();
    }

    // Same setup as Main: config, database tables, and the services that build the feeds.
    private void setUpServices() throws Exception {
        AppConfig config = new AppConfig();
        config.load();

        YouTubeClient client = new YouTubeClient(config.getApiKey());

        ChannelRepository channelRepository = new ChannelRepository();
        channelRepository.initialize();
        WatchedRepository watchedRepository = new WatchedRepository();
        watchedRepository.initialize();

        podcastService = new PodcastService(channelRepository, watchedRepository, client);
        subscriptionService = new SubscriptionService(channelRepository, watchedRepository, client, HOURS_PER_PRIORITY_POINT);
        videoOpener = new VideoOpener(config.getOperaPath(), watchedRepository);
    }

    // Tells a list how to show each video, and what to do on double-click.
    private void setUpList(ListView<Video> list) {
        // A "cell" is one row in the list. This says: for each row, show "title" on one line
        // and "channel · 3h ago" under it. JavaFX reuses rows as you scroll, so empty rows get blank text.
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Video video, boolean empty) {
                super.updateItem(video, empty);
                if (empty || video == null) {
                    setText(null);
                } else {
                    setText(video.getVideoTitle() + "\n" + video.getChannelTitle() + "  ·  " + timeAgo(video));
                }
            }
        });

        // Double-click a video: open it in Opera GX, mark it watched, and take it off the list (no API call needed).
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2) {
                return;
            }
            Video video = list.getSelectionModel().getSelectedItem();
            if (video == null) {
                return;
            }
            try {
                videoOpener.open(video.getVideoId());
                list.getItems().remove(video);
            } catch (Exception e) {
                showError("Couldn't open the video", e);
            }
        });
    }

    // Fetches both feeds from YouTube.
    // This runs on a separate background thread ("worker") so the window doesn't freeze while it waits on the internet.
    // JavaFX only lets you change the screen from its own thread, so the results get handed back with Platform.runLater.
    private void loadFeeds() {
        statusLabel.setText("Loading feeds...");

        Thread worker = new Thread(() -> {
            try {
                List<Video> podcasts = podcastService.getPodcastFeed(PODCASTS_PER_CHANNEL, true);

                List<Video> subscriptions = new ArrayList<>();
                for (ScoredVideo scored : subscriptionService.getSubscriptionFeed(SUBSCRIPTIONS_PER_CHANNEL, true)) {
                    subscriptions.add(scored.video()); // already in ranked order
                }

                Platform.runLater(() -> {
                    podcastList.getItems().setAll(podcasts);
                    subscriptionList.getItems().setAll(subscriptions);
                    statusLabel.setText("Updated " + podcasts.size() + " podcasts, " + subscriptions.size() + " subscription videos");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Couldn't load feeds");
                    showError("Couldn't load feeds", e);
                });
            }
        });
        worker.setDaemon(true); // don't keep the app running after you close the window
        worker.start();
    }

    // "45m ago", "3h ago", "2d ago"
    private static String timeAgo(Video video) {
        Instant published = video.getPublishedInstant();
        if (published == null) {
            return "";
        }
        long minutes = Duration.between(published, Instant.now()).toMinutes();
        if (minutes < 60) {
            return minutes + "m ago";
        }
        if (minutes < 60 * 24) {
            return (minutes / 60) + "h ago";
        }
        return (minutes / (60 * 24)) + "d ago";
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
