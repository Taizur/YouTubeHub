package com.youtubehub.ui;

import com.youtubehub.model.Settings;
import com.youtubehub.model.Tier;
import com.youtubehub.storage.SettingsRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

// The Settings tab: one card with a row per setting, then [Save] [Reset to defaults].
//
// Save writes the numbers to the database and hands them to the app (onSaved), which
// rebuilds the feeds from the videos it already has. No YouTube calls.
public class SettingsPane extends ScrollPane {
    private final SettingsRepository settingsRepository;
    private final Consumer<Settings> onSaved; // "a piece of code that takes a Settings": the app's apply method

    private final Spinner<Integer> podcastsPerChannel = new Spinner<>(1, 20, 5);
    private final Spinner<Integer> subscriptionsPerChannel = new Spinner<>(1, 20, 3);
    private final Spinner<Integer> hoursPerPriorityPoint = new Spinner<>(0, 168, 24, 6); // steps of 6 hours
    private final Spinner<Integer> maxAgeDays = new Spinner<>(0, 365, 0, 7); // steps of a week
    private final Label weightingExample = new Label();
    private final Label message = new Label();

    public SettingsPane(SettingsRepository settingsRepository, Settings current, Consumer<Settings> onSaved) {
        this.settingsRepository = settingsRepository;
        this.onSaved = onSaved;

        Label title = new Label("Settings");
        title.getStyleClass().add("app-title");

        VBox card = new VBox(18,
                settingRow("Podcast episodes per channel",
                        "How many recent episodes each podcast channel adds to the Podcasts tab.",
                        podcastsPerChannel),
                settingRow("Videos per subscription channel",
                        "How many recent videos each other channel adds to the Subscriptions tab.",
                        subscriptionsPerChannel),
                settingRow("Hide videos older than (days)",
                        "Leaves anything older than this out of Podcasts and Subscriptions, even if a channel "
                                + "hasn't posted enough newer ones. 0 = no limit.",
                        maxAgeDays),
                settingRow("Tier weighting (hours per tier step)",
                        "How much a channel's tier beats newness in Subscriptions. Each step up "
                                + "(Desperate → Good filler → High → Favorite) makes that channel's videos rank "
                                + "as if they were this many hours newer. 0 = newest first, ignoring tiers.",
                        hoursPerPriorityPoint),
                weightingExample);
        card.getStyleClass().add("card");
        card.setMaxWidth(720);
        weightingExample.getStyleClass().add("muted");
        weightingExample.setWrapText(true);

        Button save = new Button("Save");
        save.getStyleClass().add("accent");
        save.setOnAction(event -> save());
        Button reset = new Button("Reset to defaults");
        reset.setOnAction(event -> {
            show(Settings.DEFAULTS);
            message.setText("Defaults filled in. Click Save to use them.");
        });
        message.getStyleClass().add("message");
        HBox buttons = new HBox(10, save, reset, message);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(16, title, card, buttons);
        content.setPadding(new Insets(20, 24, 24, 24));
        setContent(content);
        setFitToWidth(true); // the content is as wide as the tab, so nothing scrolls sideways

        // Spinners you can type into, with the example sentence kept up to date
        for (Spinner<Integer> spinner : java.util.List.of(podcastsPerChannel, subscriptionsPerChannel, maxAgeDays, hoursPerPriorityPoint)) {
            spinner.setEditable(true);
            spinner.setPrefWidth(100);
            spinner.setMinWidth(100); // don't let long explanation text squash it
        }
        hoursPerPriorityPoint.valueProperty().addListener((obs, old, value) -> updateExample());

        show(current);
    }

    // One row:  Name               [ spinner ]
    //           explanation text
    private static HBox settingRow(String name, String explanation, Spinner<Integer> spinner) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("video-title");
        Label explanationLabel = new Label(explanation);
        explanationLabel.getStyleClass().add("muted");
        explanationLabel.setWrapText(true);

        VBox text = new VBox(4, nameLabel, explanationLabel);
        HBox.setHgrow(text, Priority.ALWAYS);
        Region gap = new Region();
        gap.setMinWidth(16);
        HBox row = new HBox(text, gap, spinner);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void show(Settings settings) {
        podcastsPerChannel.getValueFactory().setValue(settings.podcastsPerChannel());
        subscriptionsPerChannel.getValueFactory().setValue(settings.subscriptionsPerChannel());
        hoursPerPriorityPoint.getValueFactory().setValue(settings.hoursPerPriorityPoint());
        maxAgeDays.getValueFactory().setValue(settings.maxAgeDays());
        updateExample();
    }

    // e.g. "With 24: a Favorite video beats a Desperate one unless the Desperate one is over 3 days newer."
    private void updateExample() {
        int hours = hoursPerPriorityPoint.getValue();
        int steps = Tier.FAVORITE.level() - Tier.DESPERATE.level(); // 3 steps from bottom to top
        int boost = steps * hours;
        String amount = boost % 24 == 0 ? (boost / 24) + (boost == 24 ? " day" : " days") : boost + " hours";
        weightingExample.setText(hours == 0
                ? "With 0: Subscriptions is simply newest first."
                : "With " + hours + ": a Favorite video beats a Desperate one unless the Desperate one is more than "
                        + amount + " newer.");
    }

    private void save() {
        Settings settings = new Settings(
                commit(podcastsPerChannel),
                commit(subscriptionsPerChannel),
                commit(hoursPerPriorityPoint),
                commit(maxAgeDays));
        try {
            settingsRepository.save(settings);
            onSaved.accept(settings);
            message.setText("Saved. Feeds updated.");
        } catch (Exception e) {
            message.setText("Couldn't save: " + e.getMessage());
        }
    }

    // If you typed a number into a spinner without pressing Enter, the spinner hasn't taken it yet.
    // This takes it (or puts the old value back if what you typed isn't a number in range).
    private static int commit(Spinner<Integer> spinner) {
        try {
            String typed = spinner.getEditor().getText().trim();
            spinner.getValueFactory().setValue(Integer.parseInt(typed)); // out-of-range values get clamped
        } catch (NumberFormatException e) {
            spinner.getEditor().setText(String.valueOf(spinner.getValue()));
        }
        return spinner.getValue();
    }
}
