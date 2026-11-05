package com.youtubehub.ui;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.config.AppConfig;
import com.youtubehub.model.ScoredVideo;
import com.youtubehub.model.Settings;
import com.youtubehub.model.Video;
import com.youtubehub.player.PlayQueue;
import com.youtubehub.player.Player;
import com.youtubehub.browser.AddFromBrowser;
import com.youtubehub.browser.ExtensionServer;
import com.youtubehub.service.ChannelService;
import com.youtubehub.service.MusicService;
import com.youtubehub.service.PodcastService;
import com.youtubehub.service.SubscriptionService;
import com.youtubehub.service.VideoCache;
import com.youtubehub.service.VideoOpener;
import com.youtubehub.storage.ChannelRepository;
import com.youtubehub.storage.MusicRepository;
import com.youtubehub.storage.SettingsRepository;
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
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
//   top:    [Refresh] [Show watched]  status text   [Skip] [Stop]  Now playing: ...
//   center: tabs — Podcasts | Subscriptions | Music | Channels | Settings
//
// Only Refresh (on launch, every hour, or the button) talks to YouTube. Everything else
// rebuilds the lists from the VideoCache, which is free.
public class YouTubeHubApp extends Application {

    // Feed sizes and priority weighting. Loaded from the database on startup; the Settings tab changes them.
    private SettingsRepository settingsRepository;
    private Settings settings = Settings.DEFAULTS;

    private ChannelRepository channelRepository;
    private WatchedRepository watchedRepository;
    private VideoCache videoCache;
    private PodcastService podcastService;
    private SubscriptionService subscriptionService;
    private ChannelService channelService;
    private MusicRepository musicRepository;
    private MusicService musicService;
    private VideoOpener videoOpener;
    private Player player;
    private AddFromBrowser addFromBrowser;
    private ExtensionServer extensionServer;
    private ChannelsPane channelsPane;
    private MusicPane musicPane;

    // The rows read this to decide which videos get the "✓ Watched" look.
    // It's one Set that gets refilled, so every row always sees the latest version.
    private final Set<String> watchedIds = new HashSet<>();

    private final ListView<Video> podcastList = new ListView<>();
    private final ListView<Video> subscriptionList = new ListView<>();
    private final Button refreshButton = new Button("Refresh");
    private final CheckBox showWatched = new CheckBox("Show watched");
    private final Label statusLabel = new Label();
    private final Button previousButton = new Button("Previous");
    private final Button skipButton = new Button("Skip");
    private final Button stopButton = new Button("Stop");
    private final Label nowPlayingLabel = new Label();

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
        previousButton.setOnAction(event -> previous());
        previousButton.setDisable(true);
        skipButton.setOnAction(event -> skip());
        stopButton.setOnAction(event -> player.stop());
        skipButton.setDisable(true);
        stopButton.setDisable(true);
        // Header bar:  [▶] YouTubeHub   [Refresh] [Show watched]  Updated at ...   <gap>   Now playing ...  [Skip] [Stop]
        // The look (colours, rounded corners) comes from app.css; getStyleClass().add(...) tags a
        // control so the stylesheet can find it.
        Label logo = new Label("▶");
        logo.getStyleClass().add("logo");
        Label appTitle = new Label("YouTubeHub");
        appTitle.getStyleClass().add("app-title");
        HBox brand = new HBox(8, logo, appTitle);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 16, 0, 0));

        statusLabel.getStyleClass().add("status");
        nowPlayingLabel.getStyleClass().add("now-playing");
        // Only show the "Now playing" pill when there's something in it
        nowPlayingLabel.visibleProperty().bind(nowPlayingLabel.textProperty().isNotEmpty());
        nowPlayingLabel.managedProperty().bind(nowPlayingLabel.visibleProperty());
        nowPlayingLabel.setMaxWidth(420); // very long titles get "..." instead of pushing the buttons off

        Region gap = new Region(); // empty space that stretches, pushing the player controls to the right
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox toolbar = new HBox(10, brand, refreshButton, showWatched, statusLabel, gap, nowPlayingLabel, previousButton, skipButton, stopButton);
        toolbar.getStyleClass().add("app-header");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Tabs. setClosable(false) hides the little "x" on each tab.
        channelsPane = new ChannelsPane(channelRepository, channelService, videoCache, this::rebuildFeeds);
        musicPane = new MusicPane(musicRepository, musicService, videoOpener, player);
        Tab podcastTab = new Tab("Podcasts", podcastList);
        Tab subscriptionTab = new Tab("Subscriptions", subscriptionList);
        Tab musicTab = new Tab("Music", musicPane);
        Tab channelsTab = new Tab("Channels", channelsPane);
        Tab settingsTab = new Tab("Settings", new SettingsPane(settingsRepository, settings, this::applySettings));
        settingsTab.setClosable(false);
        podcastTab.setClosable(false);
        subscriptionTab.setClosable(false);
        musicTab.setClosable(false);
        channelsTab.setClosable(false);
        TabPane tabs = new TabPane(podcastTab, subscriptionTab, musicTab, channelsTab, settingsTab);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(tabs);

        stage.setTitle("YouTubeHub");
        // Window / taskbar icon (same picture as the Opera extension). Windows picks the size it needs.
        stage.getIcons().addAll(
                new Image(YouTubeHubApp.class.getResource("icon32.png").toExternalForm()),
                new Image(YouTubeHubApp.class.getResource("icon128.png").toExternalForm()));
        Scene scene = new Scene(root, 1150, 760);
        Theme.apply(scene); // dark theme from app.css
        stage.setScene(scene);
        stage.show();

        // Refresh now, then again every hour for as long as the app is open.
        // A Timeline is JavaFX's timer: one KeyFrame "after 1 hour, do this", repeated forever.
        startExtensionServer();
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
        musicRepository = new MusicRepository();
        musicRepository.initialize();

        videoCache = new VideoCache(client);
        podcastService = new PodcastService(channelRepository, watchedRepository, videoCache);
        settingsRepository = new SettingsRepository();
        settingsRepository.initialize();
        settings = settingsRepository.load();
        subscriptionService = new SubscriptionService(channelRepository, watchedRepository, videoCache, settings.hoursPerPriorityPoint());
        channelService = new ChannelService(channelRepository, client);
        musicService = new MusicService(musicRepository, client);
        videoOpener = new VideoOpener(config.getOperaPath(), watchedRepository);
        player = new Player(videoOpener, watchedRepository);
        addFromBrowser = new AddFromBrowser(channelService, musicService, musicRepository, videoCache);
        extensionServer = new ExtensionServer(player, addFromBrowser);
    }

    // Starts the little local server the Opera extension talks to.
    // If it can't start (e.g. the port is taken), the app still works: videos just open in new tabs,
    // and you add things with the clipboard buttons.
    private void startExtensionServer() {
        // These get called from the server's threads, so hop over to the screen's thread first.
        player.setOnChange(() -> Platform.runLater(this::playerChanged));
        addFromBrowser.setOnAdded(() -> Platform.runLater(() -> {
            channelsPane.reloadChannels();
            musicPane.reloadSongs();
            rebuildFeeds();
        }));
        try {
            extensionServer.start();
        } catch (Exception e) {
            showError("The Opera extension can't connect: couldn't start the server on port " + ExtensionServer.PORT, e);
        }
    }

    // JavaFX calls this when the window closes.
    @Override
    public void stop() {
        if (extensionServer != null) {
            extensionServer.stop();
        }
    }

    // Now-playing text, Skip/Stop buttons, and the watched marks (feed videos get marked as they load).
    private void playerChanged() {
        nowPlayingLabel.setText(player.getStatusText());
        previousButton.setDisable(!player.hasPrevious());
        skipButton.setDisable(!player.isPlaying());
        stopButton.setDisable(!player.isPlaying());
        rebuildFeeds();
    }

    // Called by the Settings tab after it saves. The cache already holds 50 videos per channel,
    // so bigger feed sizes and a new weighting are just a rebuild: no YouTube calls.
    private void applySettings(Settings newSettings) {
        settings = newSettings;
        subscriptionService.setHoursPerPriorityPoint(newSettings.hoursPerPriorityPoint());
        rebuildFeeds();
    }

    private void previous() {
        try {
            player.previous();
        } catch (Exception e) {
            showError("Couldn't go back", e);
        }
    }

    private void skip() {
        try {
            player.skip();
        } catch (Exception e) {
            showError("Couldn't skip", e);
        }
    }

    // Rows with thumbnails, double-click to open, right-click menu.
    private void setUpVideoList(ListView<Video> list) {
        list.setCellFactory(view -> new VideoListCell(watchedIds));
        list.setPlaceholder(new Label("Nothing here yet. Add channels in the Channels tab, then Refresh."));

        // Double-click: play this video, then carry on down the list (each one gets marked watched)
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                playFromSelected(list);
            }
        });

        // Right-click menu. Each item acts on whichever row is selected (right-clicking a row selects it).
        MenuItem play = new MenuItem("Play from here");
        MenuItem open = new MenuItem("Open in new tab");
        MenuItem markWatched = new MenuItem("Mark watched");
        MenuItem markUnwatched = new MenuItem("Mark unwatched");
        play.setOnAction(event -> playFromSelected(list));
        open.setOnAction(event -> openSelected(list));
        markWatched.setOnAction(event -> setSelectedWatched(list, true));
        markUnwatched.setOnAction(event -> setSelectedWatched(list, false));
        list.setContextMenu(new ContextMenu(play, open, markWatched, markUnwatched));
    }

    // Starts autoplay at the selected video. The queue is a copy of the list as it looks right now.
    private void playFromSelected(ListView<Video> list) {
        int index = list.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        try {
            player.play(PlayQueue.inOrder(list.getItems(), index, true));
        } catch (Exception e) {
            showError("Couldn't play the video", e);
        }
    }

    // The old way: a new Opera tab, no autoplay.
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
            List<Video> podcasts = podcastService.getPodcastFeed(settings.podcastsPerChannel(), hideWatched, settings.maxAgeDays());

            List<Video> subscriptions = new ArrayList<>();
            for (ScoredVideo scored : subscriptionService.getSubscriptionFeed(settings.subscriptionsPerChannel(), hideWatched, settings.maxAgeDays())) {
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
        Theme.apply(alert);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
