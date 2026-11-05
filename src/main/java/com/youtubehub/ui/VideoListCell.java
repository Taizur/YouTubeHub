package com.youtubehub.ui;

import com.youtubehub.model.Video;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// One row in a video list: rounded thumbnail on the left, title + "channel · 3h ago" on the right.
// Watched videos (when "Show watched" is on) are faded and get a green "✓ Watched" badge.
// Colours and fonts come from app.css (the style classes "thumb", "video-title", "video-meta", "badge").
public class VideoListCell extends ListCell<Video> {
    private static final double THUMB_WIDTH = 168;
    private static final double THUMB_HEIGHT = 94.5; // 16:9
    private static final double CORNER = 16;          // arc size; the corner radius is half this

    // Downloaded thumbnails, by URL, shared by every row. JavaFX reuses rows as you scroll,
    // so without this the same picture would be downloaded again and again.
    private static final Map<String, Image> THUMBNAILS = new HashMap<>();

    private final Set<String> watchedIds;

    private final ImageView thumbnail = new ImageView();
    private final Label title = new Label();
    private final Label info = new Label();
    private final Label watchedBadge = new Label("✓ Watched");
    private final HBox row;

    // watchedIds is the app's live set of watched video IDs; the app keeps it up to date.
    public VideoListCell(Set<String> watchedIds) {
        this.watchedIds = watchedIds;

        thumbnail.setFitWidth(THUMB_WIDTH);
        thumbnail.setFitHeight(THUMB_HEIGHT);
        thumbnail.setPreserveRatio(true);

        // A grey box behind the picture (shows while it downloads), with rounded corners:
        // the "clip" is a rounded rectangle, and anything outside it isn't drawn.
        StackPane thumbBox = new StackPane(thumbnail);
        thumbBox.getStyleClass().add("thumb");
        thumbBox.setMinSize(THUMB_WIDTH, THUMB_HEIGHT);
        thumbBox.setMaxSize(THUMB_WIDTH, THUMB_HEIGHT);
        Rectangle clip = new Rectangle(THUMB_WIDTH, THUMB_HEIGHT);
        clip.setArcWidth(CORNER);
        clip.setArcHeight(CORNER);
        thumbBox.setClip(clip);

        title.getStyleClass().add("video-title");
        title.setWrapText(true);
        info.getStyleClass().add("video-meta");
        watchedBadge.getStyleClass().add("badge");

        // HBox = things side by side, VBox = things stacked. The number is the gap in pixels.
        VBox text = new VBox(6, title, info, watchedBadge);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS); // the text takes whatever width is left
        row = new HBox(14, thumbBox, text);
        row.setAlignment(Pos.CENTER_LEFT);

        // Stops long titles from making the row wider than the list (which would add a sideways scroll bar).
        setPrefWidth(0);
    }

    @Override
    protected void updateItem(Video video, boolean empty) {
        super.updateItem(video, empty);

        if (empty || video == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        boolean watched = watchedIds.contains(video.getVideoId());

        title.setText(video.getVideoTitle());
        info.setText(video.getChannelTitle() + "  ·  " + timeAgo(video));
        thumbnail.setImage(getThumbnail(video.getThumbnailUrl()));

        // "managed = false" means the hidden badge doesn't leave an empty gap behind
        watchedBadge.setVisible(watched);
        watchedBadge.setManaged(watched);
        row.setOpacity(watched ? 0.55 : 1.0);

        setText(null);
        setGraphic(row); // "graphic" = custom content for the row instead of plain text
    }

    // Looks up the picture in the shared map, or starts downloading it.
    // The last "true" = download in the background, so scrolling doesn't stutter.
    private static Image getThumbnail(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        return THUMBNAILS.computeIfAbsent(url, u -> new Image(u, THUMB_WIDTH, THUMB_HEIGHT, true, true, true));
    }

    // "45m ago", "3h ago", "2d ago", "5mo ago", "9y ago" (old music videos would otherwise say "3583d ago")
    private static String timeAgo(Video video) {
        Instant published = video.getPublishedInstant();
        if (published == null) {
            return "";
        }
        long minutes = Duration.between(published, Instant.now()).toMinutes();
        long days = minutes / (60 * 24);
        if (minutes < 60) {
            return minutes + "m ago";
        }
        if (minutes < 60 * 24) {
            return (minutes / 60) + "h ago";
        }
        if (days < 30) {
            return days + "d ago";
        }
        if (days < 365) {
            return (days / 30) + "mo ago";
        }
        return (days / 365) + "y ago";
    }
}
