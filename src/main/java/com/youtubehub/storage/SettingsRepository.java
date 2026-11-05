package com.youtubehub.storage;

import com.youtubehub.model.Settings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

// Saves the Settings tab's numbers in youtubehub.db, in a small "name = value" table:
//
//   key                         value
//   podcasts_per_channel        5
//   subscriptions_per_channel   3
//   hours_per_priority_point    24
//   max_age_days                0
//
// A setting that was never saved (e.g. the first time the app runs) uses its default.
public class SettingsRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:youtubehub.db";

    private static final String PODCASTS_PER_CHANNEL = "podcasts_per_channel";
    private static final String SUBSCRIPTIONS_PER_CHANNEL = "subscriptions_per_channel";
    private static final String HOURS_PER_PRIORITY_POINT = "hours_per_priority_point";
    private static final String MAX_AGE_DAYS = "max_age_days";

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // Creates the settings table the first time the app runs. Safe to call every startup.
    public void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public Settings load() throws SQLException {
        Map<String, String> saved = new HashMap<>();
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT key, value FROM settings;")) {
            while (results.next()) {
                saved.put(results.getString("key"), results.getString("value"));
            }
        }

        Settings defaults = Settings.DEFAULTS;
        return new Settings(
                readInt(saved, PODCASTS_PER_CHANNEL, defaults.podcastsPerChannel()),
                readInt(saved, SUBSCRIPTIONS_PER_CHANNEL, defaults.subscriptionsPerChannel()),
                readInt(saved, HOURS_PER_PRIORITY_POINT, defaults.hoursPerPriorityPoint()),
                readInt(saved, MAX_AGE_DAYS, defaults.maxAgeDays()));
    }

    public void save(Settings settings) throws SQLException {
        // "Upsert": add the row, or if that key is already there, replace its value.
        String sql = """
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value;
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            put(statement, PODCASTS_PER_CHANNEL, settings.podcastsPerChannel());
            put(statement, SUBSCRIPTIONS_PER_CHANNEL, settings.subscriptionsPerChannel());
            put(statement, HOURS_PER_PRIORITY_POINT, settings.hoursPerPriorityPoint());
            put(statement, MAX_AGE_DAYS, settings.maxAgeDays());
        }
    }

    private static void put(PreparedStatement statement, String key, int value) throws SQLException {
        statement.setString(1, key);
        statement.setString(2, String.valueOf(value));
        statement.executeUpdate();
    }

    // The saved number, or the default if it's missing or somehow not a number.
    private static int readInt(Map<String, String> saved, String key, int defaultValue) {
        try {
            return Integer.parseInt(saved.get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
