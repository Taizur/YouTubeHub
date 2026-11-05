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
    private String operaPath;

    public void load() throws IOException {
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            properties.load(reader);
        }

        apiKey = properties.getProperty("youtube.api.key");
        operaPath = properties.getProperty("opera.path");
    }

    public String getApiKey() {
        return apiKey;
    }

    // Full path to Opera GX's opera.exe, or null if config.properties doesn't have opera.path.
    public String getOperaPath() {
        return operaPath;
    }
}
