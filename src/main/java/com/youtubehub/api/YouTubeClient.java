package com.youtubehub.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtubehub.model.Channel;
import com.youtubehub.model.Video;


import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
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
        return getChannelWhere("id=" + channelId);
    }

    // Same as getChannel, but finds the channel by its handle, like "@joerogan" (also 1 quota unit).
    public Channel getChannelByHandle(String handle) throws IOException, InterruptedException {
        // URLEncoder makes the handle safe to put in a link (handles can contain non-English letters)
        return getChannelWhere("forHandle=" + URLEncoder.encode(handle, StandardCharsets.UTF_8));
    }

    // Shared by getChannel and getChannelByHandle. `filter` says which channel: "id=UC..." or "forHandle=@...".
    private Channel getChannelWhere(String filter) throws IOException, InterruptedException {
        String json = get("/channels?part=snippet,contentDetails&" + filter);
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

    // YouTube has no "this is a Short" field, so anything this long or shorter counts as one.
    // Shorts can be up to 3 minutes. A normal video under 3 minutes gets hidden too (rare for these channels).
    private static final int SHORTS_MAX_SECONDS = 180;

    // Returns up to maxResults of the newest videos in the playlist, never including streams or Shorts.
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

        // Never show live streams, stream VODs, or Shorts.
        Set<String> hiddenIds = findStreamsAndShorts(videos);
        videos.removeIf(video -> hiddenIds.contains(video.getVideoId()));

        // Keep only the newest maxResults (the playlist is already newest first)
        if (videos.size() > maxResults) {
            return new ArrayList<>(videos.subList(0, maxResults));
        }
        return videos;
    }

    // Asks YouTube about all these videos in one request (1 quota unit, max 50 videos) and returns the IDs to hide:
    //   - streams: they come back with a "liveStreamingDetails" section; normal uploads don't have one
    //   - Shorts: contentDetails.duration is SHORTS_MAX_SECONDS or less
    private Set<String> findStreamsAndShorts(List<Video> videos) throws IOException, InterruptedException {
        Set<String> hiddenIds = new HashSet<>();
        if (videos.isEmpty()) {
            return hiddenIds;
        }

        List<String> ids = new ArrayList<>();
        for (Video video : videos) {
            ids.add(video.getVideoId());
        }

        // Asking for two parts in the same request still costs 1 unit
        String json = get("/videos?part=contentDetails,liveStreamingDetails&id=" + String.join(",", ids));
        JsonNode items = mapper.readTree(json).path("items");

        for (JsonNode item : items) {
            String id = item.path("id").asText();

            if (item.has("liveStreamingDetails")) {
                hiddenIds.add(id);
                continue;
            }

            long seconds = durationSeconds(item.path("contentDetails").path("duration").asText());
            if (seconds > 0 && seconds <= SHORTS_MAX_SECONDS) {
                hiddenIds.add(id);
            }
        }
        return hiddenIds;
    }

    // YouTube writes lengths like "PT41M2S" (41 minutes 2 seconds) or "PT1H5M" (1 hour 5 minutes).
    // Java's Duration.parse understands that format. Returns 0 if it's missing or unreadable.
    private static long durationSeconds(String isoDuration) {
        try {
            return Duration.parse(isoDuration).getSeconds();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
