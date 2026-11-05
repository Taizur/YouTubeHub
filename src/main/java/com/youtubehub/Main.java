package com.youtubehub;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.config.AppConfig;
import com.youtubehub.model.Video;

public class Main {
    public static void main(String[] args) {
        try {
            AppConfig appConfig = new AppConfig();
            appConfig.load();
            System.out.println("Key loaded, length: " + appConfig.getApiKey().length());

            YouTubeClient client = new YouTubeClient(appConfig.getApiKey());
            Video testVideo = client.getVideo("d9y30vB88zg");
            System.out.println(client.getUploadsPlaylistId("UCzQUP1qoWDoEbmsQxvdjxgQ"));

        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }


    }
}
