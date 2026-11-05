package com.youtubehub;

import com.youtubehub.api.YouTubeClient;
import com.youtubehub.config.AppConfig;
import com.youtubehub.model.Video;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            AppConfig appConfig = new AppConfig();
            appConfig.load();
            System.out.println("Key loaded, length: " + appConfig.getApiKey().length());

            YouTubeClient client = new YouTubeClient(appConfig.getApiKey());
            Video testVideo = client.getVideo("d9y30vB88zg");
            System.out.println(client.getUploadsPlaylistId("UCzQUP1qoWDoEbmsQxvdjxgQ"));
            List<Video> videos = new ArrayList<>(client.getRecentUploads("UCzQUP1qoWDoEbmsQxvdjxgQ"));
            for  (Video video : videos) {
                System.out.println(video.getVideoTitle());
            }




        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }


    }
}
