package com.youtubehub.api;

import java.net.URI;
import java.net.URISyntaxException;

// What a pasted YouTube link points to.
//   type  = what kind of thing it is (see Type below)
//   value = the useful part: a channel ID (UC...), a handle (@name), or a video ID
//
// Use YouTubeLink.parse(text) to turn copied text into one of these.
public record YouTubeLink(Type type, String value) {

    // An enum is a fixed list of options, like a dropdown. A link is always exactly one of these.
    public enum Type {
        CHANNEL_ID, // youtube.com/channel/UC...
        HANDLE,     // youtube.com/@joerogan
        VIDEO_ID    // youtube.com/watch?v=..., youtu.be/..., youtube.com/shorts/..., youtube.com/live/...
    }

    // Reads a copied link (or a bare UC... ID or @handle) and works out what it points to.
    // Returns null if it isn't a YouTube link we understand.
    public static YouTubeLink parse(String text) {
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return null;
        }

        // Not a link, just an ID or handle on its own
        if (text.startsWith("@")) {
            return new YouTubeLink(Type.HANDLE, text);
        }
        if (text.startsWith("UC") && !text.contains("/")) {
            return new YouTubeLink(Type.CHANNEL_ID, text);
        }

        // Links copied from the address bar usually start with https://, but allow it to be missing
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://" + text;
        }

        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            return null; // not a valid link at all
        }

        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null) {
            return null;
        }
        host = host.toLowerCase();

        // Split "/channel/UC123/videos" into ["", "channel", "UC123", "videos"]
        String[] parts = path.split("/");
        String first = parts.length > 1 ? parts[1] : "";
        String second = parts.length > 2 ? parts[2] : "";

        // youtu.be/VIDEO_ID (the short links from YouTube's Share button)
        if (host.equals("youtu.be")) {
            return first.isEmpty() ? null : new YouTubeLink(Type.VIDEO_ID, first);
        }

        // youtube.com, www.youtube.com, m.youtube.com, music.youtube.com
        if (!host.equals("youtube.com") && !host.endsWith(".youtube.com")) {
            return null;
        }

        if (first.startsWith("@")) {
            return new YouTubeLink(Type.HANDLE, first);
        }
        if (first.equals("channel") && !second.isEmpty()) {
            return new YouTubeLink(Type.CHANNEL_ID, second);
        }
        if ((first.equals("shorts") || first.equals("live")) && !second.isEmpty()) {
            return new YouTubeLink(Type.VIDEO_ID, second);
        }
        if (first.equals("watch")) {
            String videoId = getQueryValue(uri.getRawQuery(), "v");
            return videoId == null ? null : new YouTubeLink(Type.VIDEO_ID, videoId);
        }

        return null; // some other YouTube page (home, search results, a playlist, ...)
    }

    // Finds one value in a query string. For "v=abc123&t=42s" and name "v", returns "abc123".
    private static String getQueryValue(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] keyAndValue = pair.split("=", 2);
            if (keyAndValue.length == 2 && keyAndValue[0].equals(name) && !keyAndValue[1].isEmpty()) {
                return keyAndValue[1];
            }
        }
        return null;
    }
}
