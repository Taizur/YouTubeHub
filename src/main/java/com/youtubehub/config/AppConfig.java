package com.youtubehub.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AppConfig {
    private static final Path CONFIG_PATH = Path.of("config.properties");
    private String apiKey;

    public void load() throws IOException {
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            properties.load(reader);
        }

        apiKey = properties.getProperty("youtube.api.key");
    }

    public String getApiKey() {
        return apiKey;
    }
}
