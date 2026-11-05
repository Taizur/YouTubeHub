package com.youtubehub.ui;

import com.youtubehub.model.Video;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// One row in a video list: thumbnail on the left, title + "channel · 3h ago" on the right.
// Watched videos (when "Show watched" is on) are faded and marked with a check.
public class VideoListCell extends ListCell<Video> {
    private static final double THUMB_WIDTH = 160;
    private static final double THUMB_HEIGHT = 90;

    // Downloaded thumbnails, by URL, shared by every row. JavaFX reuses rows as you scroll,
    // so without this the same picture would be downloaded again and again.
    private static final Map<String, Image> THUMBNAILS = new HashMap<>();

    private final Set<String> watchedIds;

    private final ImageView thumbnail = new ImageView();
    private final Label title = new Label();
    private final Label info = new Label();
    private final HBox row;

    // watchedIds is the app's live set of watched video IDs; the app keeps it up to date.
    public VideoListCell(Set<String> watchedIds) {
        this.watchedIds = watchedIds;

        thumbnail.setFitWidth(THUMB_WIDTH);
        thumbnail.setFitHeight(THUMB_HEIGHT);
        thumbnail.setPreserveRatio(true);

        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        title.setWrapText(true);
        info.setStyle("-fx-text-fill: gray;");

        // HBox = things side by side, VBox = things stacked. Spacing is the gap in pixels.
        VBox text = new VBox(4, title, info);
        row = new HBox(10, thumbnail, text);
        row.setPadding(new Insets(4));
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
        info.setText((watched ? "✓ Watched  ·  " : "") + video.getChannelTitle() + "  ·  " + timeAgo(video));
        thumbnail.setImage(getThumbnail(video.getThumbnailUrl()));
        row.setOpacity(watched ? 0.5 : 1.0);

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
}
