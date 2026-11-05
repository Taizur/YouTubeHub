package com.youtubehub.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.youtubehub.model.Playlist;
import com.youtubehub.player.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// A tiny web server that only this PC can reach (127.0.0.1), so the Opera extension can talk to the app.
// It uses the HttpServer that comes with the JDK, so no extra library.
//
// Player (from extension/content.js; every request also carries ?tab=<Opera's tab id>):
//   GET /hello?video=ID   "a YouTube page just loaded with this video"  -> {"player": true/false}
//   GET /poll             "anything for me?" (held open up to 20 s)     -> {"command": "load", "videoId": "..."}
//                                                                          or {"command": "none"} / {"command": "stop"}
//   GET /ended?video=ID   "this video just finished"                    -> same answers as /poll
//
// Adding (from extension/popup.js):
//   GET /playlists                                   -> {"playlists": [{"id": 1, "name": "Metal"}, ...]}
//   GET /add-song?url=...&playlist=ID                -> {"ok": true/false, "message": "..."}
//   GET /add-channel?url=...&priority=4&podcast=true -> {"ok": true/false, "message": "..."}  (priority = tier level 1–4)
public class ExtensionServer {
    public static final int PORT = 8765; // must match APP_URL in extension/background.js and popup.js

    private final Player player;
    private final AddFromBrowser adder;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService threads;

    public ExtensionServer(Player player, AddFromBrowser adder) {
        this.player = player;
        this.adder = adder;
    }

    public void start() throws IOException {
        // 127.0.0.1 = "this computer only". Other devices on your Wi-Fi can't connect.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/", this::handle);

        // /poll waits up to 20 seconds, so each request gets its own thread instead of queueing behind it.
        // Daemon threads don't keep the app running after the window closes.
        threads = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "extension-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(threads);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            threads.shutdownNow();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            // Normal websites send an Origin like "https://somesite.com". The extension sends
            // "chrome-extension://..." (or none). Refuse websites so no page can drive the app.
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && origin.startsWith("http")) {
                send(exchange, 403, mapper.createObjectNode().put("error", "forbidden"));
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            int tab = Integer.parseInt(query.getOrDefault("tab", "-1"));
            String video = query.get("video");
            String url = query.getOrDefault("url", "");

            ObjectNode answer = mapper.createObjectNode();
            switch (exchange.getRequestURI().getPath()) {
                case "/hello" -> answer.put("player", player.hello(tab, video));
                case "/poll" -> command(answer, player.poll(tab));
                case "/ended" -> command(answer, player.ended(tab, video));
                case "/playlists" -> {
                    ArrayNode list = answer.putArray("playlists");
                    for (Playlist playlist : adder.getPlaylists()) {
                        list.addObject().put("id", playlist.id()).put("name", playlist.name());
                    }
                }
                case "/add-song" -> {
                    String playlist = query.getOrDefault("playlist", "");
                    Integer playlistId = playlist.isEmpty() ? null : Integer.valueOf(playlist);
                    result(answer, () -> adder.addSong(url, playlistId));
                }
                case "/add-channel" -> {
                    int priority = Integer.parseInt(query.getOrDefault("priority", "2")); // tier level; 2 = Good filler
                    boolean podcast = Boolean.parseBoolean(query.get("podcast"));
                    result(answer, () -> adder.addChannel(url, priority, podcast));
                }
                default -> {
                    send(exchange, 404, answer.put("error", "unknown path"));
                    return;
                }
            }
            send(exchange, 200, answer);
        } catch (Exception e) {
            send(exchange, 500, mapper.createObjectNode().put("error", String.valueOf(e.getMessage())));
        }
    }

    // null = you're not the player tab, "" = nothing to do, anything else = a video id to load.
    private static void command(ObjectNode answer, String videoId) {
        if (videoId == null) {
            answer.put("command", "stop");
        } else if (videoId.isEmpty()) {
            answer.put("command", "none");
        } else {
            answer.put("command", "load");
            answer.put("videoId", videoId);
        }
    }

    // Like Runnable, but the code gives back a message and is allowed to throw.
    private interface Action {
        String run() throws Exception;
    }

    // Runs an add, turning the outcome into {"ok": ..., "message": ...} for the popup to show.
    private static void result(ObjectNode answer, Action action) {
        try {
            answer.put("ok", true).put("message", action.run());
        } catch (IllegalArgumentException e) {
            answer.put("ok", false).put("message", e.getMessage()); // e.g. "That's a channel link..."
        } catch (Exception e) {
            answer.put("ok", false).put("message", "Couldn't add: " + e.getMessage());
        }
    }

    private void send(HttpExchange exchange, int status, ObjectNode body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // "tab=12&video=abc" -> {tab=12, video=abc}
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                result.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
