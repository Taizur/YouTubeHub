package com.youtubehub.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // Looks up a channel's name and uploads playlist in one request (costs 1 quota unit).
    // Returns a Channel with default settings (priority 0, not a podcast), or null if no channel has that ID.
    // The caller decides the real priority/podcast values and sets them afterward.
    public Channel getChannel(String channelId) throws IOException, InterruptedException {
        String json = get("/channels?part=snippet,contentDetails&id=" + channelId);
        JsonNode root = mapper.readTree(json);

        JsonNode items = root.path("items");
        if (items.isEmpty()) {
            return null; // no channel with that ID
        }

        JsonNode item = items.get(0);
        return new Channel(
                item.path("id").asText(),
                item.path("snippet").path("title").asText(),
                item.path("contentDetails").path("relatedPlaylists").path("uploads").asText(),
                0,
                false
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

    // maxResults = how many of the newest videos to fetch. YouTube only allows 1 to 50 per request,
    // so anything outside that range gets pulled back inside it.
    public String getPlaylistItemsJson(String playlistId, int maxResults) throws IOException, InterruptedException {
        int safeMax = Math.max(1, Math.min(50, maxResults));
        return get("/playlistItems?part=snippet,contentDetails&playlistId=" + playlistId + "&maxResults=" + safeMax);
    }

    public List<Video> getRecentUploads(String channelId, int maxResults) throws IOException, InterruptedException {
        String playlistId = getUploadsPlaylistId(channelId);
        if (playlistId == null) {
            return new ArrayList<>();
        }

        return getVideosFromPlaylist(playlistId, maxResults);
    }

    // How many recent uploads to look through. Costs the same 1 quota unit whether it's 5 or 50,
    // and a bigger batch leaves room to skip streams and still have enough videos.
    private static final int UPLOADS_TO_SCAN = 50;

    // Returns up to maxResults of the newest videos in the playlist, never including streams.
    public List<Video> getVideosFromPlaylist(String playlistId, int maxResults) throws IOException, InterruptedException {
        String json = getPlaylistItemsJson(playlistId, UPLOADS_TO_SCAN);
        JsonNode root = mapper.readTree(json);
        JsonNode items = root.path("items");

        if (items.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Video> videos = new ArrayList<>();

        for  (JsonNode item : items) {
            JsonNode snippet = item.path("snippet");
            JsonNode contentDetails = item.path("contentDetails");
            videos.add(new Video(
                    contentDetails.path("videoId").asText(),
                    snippet.path("title").asText(),
                    snippet.path("channelId").asText(),
                    snippet.path("channelTitle").asText(),
                    contentDetails.path("videoPublishedAt").asText()
            ));
        }

        // Never show live streams or stream VODs: drop any video YouTube says was a stream.
        Set<String> streamIds = findStreamIds(videos);
        videos.removeIf(video -> streamIds.contains(video.getVideoId()));

        // Keep only the newest maxResults (the playlist is already newest first)
        if (videos.size() > maxResults) {
            return new ArrayList<>(videos.subList(0, maxResults));
        }
        return videos;
    }

    // Asks YouTube which of these videos are (or were) live streams, all in one request (1 quota unit, max 50 videos).
    // Streams come back with a "liveStreamingDetails" section; normal uploads don't have one.
    // Returns the IDs of the streams.
    private Set<String> findStreamIds(List<Video> videos) throws IOException, InterruptedException {
        Set<String> streamIds = new HashSet<>();
        if (videos.isEmpty()) {
            return streamIds;
        }

        List<String> ids = new ArrayList<>();
        for (Video video : videos) {
            ids.add(video.getVideoId());
        }

        String json = get("/videos?part=liveStreamingDetails&id=" + String.join(",", ids));
        JsonNode items = mapper.readTree(json).path("items");

        for (JsonNode item : items) {
            if (item.has("liveStreamingDetails")) {
                streamIds.add(item.path("id").asText());
            }
        }
        return streamIds;
    }
}
