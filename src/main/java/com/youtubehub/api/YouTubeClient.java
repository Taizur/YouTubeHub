package com.youtubehub.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtubehub.model.Video;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class YouTubeClient {
    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public YouTubeClient(String apiKey) {
        this.apiKey = apiKey;
    }

    // Fetches the raw JSON for a single video's basic details (title, channel, upload date, etc.)
    public String getVideoJson(String videoId) throws IOException, InterruptedException {
        return get("/videos?part=snippet&id=" + videoId);
    }

    // Shared helper: every YouTube call goes through here. Pass the endpoint + query,
    // and it adds the base URL and API key, sends the request, and returns the JSON body.
    private String get(String endpointAndQuery) throws IOException, InterruptedException {
        String url = BASE_URL + endpointAndQuery + "&key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("YouTube API returned status " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    public Video getVideo(String videoId) throws IOException, InterruptedException {
        String json = getVideoJson(videoId);
        JsonNode root = mapper.readTree(json);

        JsonNode items = root.path("items");
        if (items.isEmpty()) {
            return null; // no video with that ID
        }

        JsonNode item = items.get(0);
        JsonNode snippet = item.path("snippet");

        return new Video(
                item.path("id").asText(),
                snippet.path("title").asText(),
                snippet.path("channelId").asText(),
                snippet.path("channelTitle").asText(),
                snippet.path("publishedAt").asText()
        );
    }

    public String getUploadsPlaylistId(String channelId) throws IOException, InterruptedException {
        String json = get("/channels?part=contentDetails&id=" + channelId);
        JsonNode root = mapper.readTree(json);

        JsonNode items = root.path("items");
        if (items.isEmpty()) {
            return null;
        }
        JsonNode item = items.get(0);
        return item.path("contentDetails").path("relatedPlaylists").path("uploads").asText();

    }
}
