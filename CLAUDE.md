# YouTubeHub — Project Handoff

Personal Java desktop app for a customized YouTube experience. It's both a tool Brandon will actually use and a learning project for his Java skills.

## Goals (three main areas)

1. **Podcasts** — a consolidated, chronological feed of recent episodes from specific podcast channels (e.g. JRE, Sae Bae Cast, Wall Friends). Not tracking guest appearances on other channels.
2. **Subscriptions** — a feed of recent uploads from non-podcast channels, ranked by a weighted score of **channel priority + recency**. Priority must not completely override recency (a 2-hour-old video from a low-priority channel should generally beat a 3-week-old video from a high-priority channel). The weighting should be easy to tweak later. This is NOT a recreation of YouTube's recommendation algorithm.
3. **Music** — genre-based playlists (rock, metal, electronic, etc.), a master playlist, songs can belong to multiple playlists, a local music library. Syncing with real YouTube playlists would require OAuth (see below). Build this last.

Other planned features:
- **Watched tracking (local)** — the API does not expose watch history. Clicking a video in YouTubeHub opens it and marks it watched locally (even if not finished). Watched videos can be hidden; manual mark watched/unwatched. Watching directly on YouTube won't update the app.
- **Browser integration** — clicking a video opens it in a **new tab in Opera GX** on Windows (not the default browser). The Opera GX executable path should be configurable. No built-in video player.
- **UI** — JavaFX, later. Channel management (add/remove channels, set priority, mark as podcast) should happen through the UI — channels are NOT hardcoded.

## Tech stack

- Java 21, Maven, IntelliJ IDEA, Windows
- YouTube Data API v3 via **plain `java.net.http.HttpClient`** (not Google's client library, even though it's in the pom — chosen so the requests/JSON are visible and understandable)
- **Jackson** (`jackson-databind`) using the tree model: `readTree` → `.path()` → `.get(i)` → `.asText()`
- **SQLite** via `org.xerial:sqlite-jdbc`, plain JDBC with prepared statements
- JavaFX 21.0.13 (`javafx-controls`) + `javafx-maven-plugin` 0.0.8

## Auth decision

Using an **API key only** (public data), no OAuth for now. OAuth would only be needed later for pulling the real subscription list or syncing playlists to the YouTube account. Note: OAuth apps left in "testing" mode reportedly get refresh tokens that expire after ~7 days — verify before relying on it.

## Secrets and local files

- `config.properties` in the **project root** holds `youtube.api.key=...` and `opera.path=...`. It is gitignored. **Never commit it, print it, or log the full request URL** (the key is in the query string).
- `youtubehub.db` (SQLite database, created automatically in the project root at runtime) is gitignored.
- Repo is on GitHub (private).

## Quota notes

- Free daily quota; no billing account needed.
- `videos`, `channels`, `playlistItems` list calls cost ~1 unit each. **`search` costs 100 — avoid it.**
- Get a channel's videos via its **uploads playlist** (`UU...` ID from `channels?part=contentDetails` → `contentDetails.relatedPlaylists.uploads`), then `playlistItems`. The uploads playlist ID is looked up once when a channel is added and **saved in the database** so it never needs re-fetching.

## Current code (all under `src/main/java/com/youtubehub/`)

| File | Status / purpose |
|---|---|
| `Launcher.java` | **Starts the real app.** `main` calls `Application.launch(YouTubeHubApp.class)` (running a class that extends `Application` directly from IntelliJ fails with "JavaFX runtime components are missing"). Also `mvn javafx:run`. |
| `ui/YouTubeHubApp.java` | JavaFX `Application`. Toolbar (Refresh button, "Show watched" checkbox, status "Updated at 10:22 AM") over a `TabPane`: Podcasts, Subscriptions (`ListView<Video>` with `VideoListCell`), Channels (`ChannelsPane`). `refreshFromYouTube()` = `VideoCache.refreshAll` on a background daemon `Thread`, then `Platform.runLater(rebuildFeeds)`; runs on launch and every hour (`Timeline`). `rebuildFeeds()` rebuilds both lists from the cache (no API). Double-click or right-click "Open in Opera GX" → `VideoOpener.open`; right-click "Mark watched/unwatched". Shared `watchedIds` Set is refilled on each rebuild for the row markers. Feed sizes/weight are constants at the top for now. The harmless "Unsupported JavaFX configuration: classes were loaded from 'unnamed module'" warning comes from running on the classpath. |
| `Main.java` | Test bench only. Currently loads config, initializes the DB, builds the podcast feed and prints it. |
| `config/AppConfig.java` | Loads `config.properties` with `Properties`; `load()`, `getApiKey()`, `getOperaPath()` (`opera.path`, currently `C:/Users/brand/AppData/Local/Programs/Opera GX/opera.exe`). |
| `api/YouTubeClient.java` | Talks to YouTube. Private `get(endpointAndQuery)` helper adds base URL + key, sends request, throws `IOException` on non-200. Methods: `getVideoJson(id)`, `getVideo(id)` → `Video`, `getUploadsPlaylistId(channelId)` → `UU...` or null, `getPlaylistItemsJson(playlistId, maxResults)` (clamped to 1–50, the API limit), `getRecentUploads(channelId, maxResults)` → `List<Video>`, `getVideosFromPlaylist(playlistId, maxResults)` → `List<Video>`, **always drops live streams/VODs and Shorts** (private `findStreamsAndShorts` does one `videos?part=contentDetails,liveStreamingDetails` call, 1 unit: a `liveStreamingDetails` section = stream; `contentDetails.duration` <= `SHORTS_MAX_SECONDS` (180) = Short, since the API has no Short flag). Brandon never wants VODs or Shorts shown. It scans the newest 50 uploads (`UPLOADS_TO_SCAN`, same 1 unit), drops streams, then keeps the newest `maxResults`. Subscription feed uses 3 per channel, podcasts 5. |
| `model/Video.java` | `videoId`, `videoTitle`, `channelId`, `channelTitle`, `publishedAt` (ISO-8601 like `2026-10-29T17:00:32Z`), `thumbnailUrl` (`snippet.thumbnails.medium.url`, falls back to `default`; "" if none). Getters, plus `getPublishedInstant()` → `Instant` or null if blank. |
| `ui/VideoListCell.java` | List row: 160x90 thumbnail (`ImageView`, background-loaded, shared static URL→`Image` map) + bold title + "channel · 3h ago". Watched rows faded with "✓ Watched". |
| `ui/ChannelsPane.java` | Channels tab. Top: priority `Spinner` (1–10), Podcast `CheckBox`, "Add from clipboard" (reads `Clipboard` on FX thread, then `ChannelService.addChannelFromLink` + `VideoCache.refreshChannel` on a worker). Center: channel list (priority desc). Bottom: edit selected (priority, podcast, Save, Remove with confirm). Calls a `Runnable` so the app rebuilds feeds. |
| `service/VideoCache.java` | In-memory `channelId → List<Video>` (50 newest, streams/Shorts already removed). `refreshAll(channels)` / `refreshChannel(channel)` are the **only** API calls for feeds; `getVideos(channelId)`; map is swapped in whole (`volatile`) so the UI never sees a half-filled one. |
| `model/Channel.java` | `channelId`, `channelTitle`, `uploadsPlaylistId`, `priority` (int), `podcast` (boolean). Getters + `setPriority`, `setPodcast`. |
| `storage/ChannelRepository.java` | SQLite `channels` table. `initialize()`, `addChannel` (upsert), `getAllChannels`, `removeChannel`, `updatePriority`, `updatePodcast`. |
| `api/YouTubeLink.java` | Record `(Type type, String value)`, `Type` enum `CHANNEL_ID / HANDLE / VIDEO_ID`. `static parse(text)` reads any copied YouTube link (`/@handle`, `/channel/UC...`, `watch?v=`, `youtu.be/`, `/shorts/`, `/live/`, m./music. hosts, bare `UC...` or `@handle`) or returns null. Pure Java, no API calls. |
| `service/ChannelService.java` | `addChannel(channelId, priority, podcast)` — looks up title + uploads playlist via `YouTubeClient.getChannel(id)` (1 unit), saves to DB, returns the `Channel`; throws `IllegalArgumentException` for an unknown ID. Also `addChannelFromLink(text, priority, podcast)` — parses with `YouTubeLink`; handle → `YouTubeClient.getChannelByHandle` (`channels?forHandle=`, 1 unit); video link → that video's channel. The UI's "Add from clipboard" button will call this. |
| `storage/WatchedRepository.java` | SQLite `watched_videos` table (`video_id` PK, `watched_at` ISO text). `initialize()`, `markWatched(id)` (upsert, updates time), `markUnwatched(id)`, `isWatched(id)`, `getWatchedIds()` → `Set<String>`. |
| `service/VideoOpener.java` | `open(videoId)` — runs `opera.exe <watch URL>` via `ProcessBuilder` (adds a tab to the open Opera window), then `markWatched`. Throws `IllegalStateException` if `opera.path` is missing/wrong; a failed launch never marks watched. Constructor: `(operaPath, WatchedRepository)`. |
| `service/FeedFilter.java` | `static newest(videos, limit, hideWatched, watchedIds)` — keeps the first `limit` videos, skipping watched ones before counting so channels still fill up. Used by both feeds. |
| `service/PodcastService.java` | `getPodcastFeed(videosPerChannel, hideWatched)` — all channels where `podcast == true`, reads each channel's videos from `VideoCache`, `FeedFilter.newest`, combine, sort newest first (string sort on ISO date). No API calls. Constructor takes `ChannelRepository, WatchedRepository, VideoCache`. |
| `service/SubscriptionService.java` | `getSubscriptionFeed(videosPerChannel, hideWatched)` → `List<ScoredVideo>` (constructor: `ChannelRepository, WatchedRepository, VideoCache, hoursPerPriorityPoint`; reads from the cache, no API calls), non-podcast channels ranked by effective age: `realAgeHours - priority * hoursPerPriorityPoint` (lowest first). `hoursPerPriorityPoint` is a constructor arg (Main uses 24); later it becomes a UI setting. |
| `model/ScoredVideo.java` | Record: `video`, `priority`, `ageHours`, `effectiveAgeHours`. |
| `service/Music.java` | Empty placeholder. |

Saved test data: JRE (`UCzQUP1qoWDoEbmsQxvdjxgQ`, uploads `UUzQUP1qoWDoEbmsQxvdjxgQ`), priority 5, podcast = true.

Podcast vs subscription split: a single `podcast` boolean. Podcast feed = `podcast == true`, subscription feed = `podcast == false`. If a channel ever needs to be in both feeds, split into two booleans.

## Refresh policy (decided)

Fetch from YouTube only: on launch, automatically once per hour, and on a manual Refresh button. Everything else (marking watched, hiding watched, re-sorting, opening a video) works on the already-fetched videos kept in memory — no API calls. Budget: roughly 2 units per channel per refresh, so ~30 refreshes/day is a tiny fraction of the 10,000/day quota.

## Adding channels/songs (decided)

No hunting for IDs. In the UI: copy the address bar in Opera (Ctrl+L, Ctrl+C), click **"Add from clipboard"**; the app reads the clipboard and uses `YouTubeLink.parse`. Never search by name (`search` = 100 units). A possible later add-on: an Opera GX extension (JavaScript) that sends the current tab URL to a localhost-only HTTP server in the app — reuses the same parsing.

## Next steps (rough order)

1. ~~Make `maxResults` a parameter~~ (done). Still to consider: an age cutoff (e.g. last N days) as an addition.
2. Channels are never hardcoded: they get added through the UI (step 6) via `ChannelService.addChannel` (done). Main can call it temporarily to put test data in the DB.
3. ~~**Subscription feed service**~~ (done; tune `hoursPerPriorityPoint`). — non-podcast channels, ranked by a tunable priority/recency score. Per-channel priority is already in the DB; the overall weighting should become a user setting editable in the UI. Convert `publishedAt` to `Instant` for the math.
4. ~~Watched tracking~~ (done: `WatchedRepository` + `hideWatched` on both feeds). Still to do in the UI: show a watched marker when not hiding.
5. ~~Open videos in a new Opera GX tab~~ (done: `VideoOpener`). The UI's click handler will call it.
6. JavaFX UI — functional version done (feeds with thumbnails, refresh, watched marks, channel management). Still to do: polish (layout/styling), settings screen (feed sizes, `hoursPerPriorityPoint`), Music section.
7. Music library and playlists (own tables; YouTube sync would need OAuth). **Music is song-based, not channel-based**: Brandon adds individual videos he likes (by video ID/URL), regardless of channel. Planned tables: `songs` (video ID, title, channel title, added timestamp — filled via `getVideo(id)`, 1 unit), `playlists` (id, name, e.g. "Metal"), `playlist_songs` (playlist id + video ID, links a song to any number of playlists). The master playlist = every row in `songs` (no separate table needed). Music does not use the `channels` table.

## How Brandon wants to work

- He's a CS student, about first-year level in practice. Comfortable with Java basics (classes, objects, loops, ArrayLists). Less familiar with exception handling, file paths, library APIs (Jackson, JDBC, HttpClient) and their syntax.
- Explain new concepts in **plain, non-technical language**. Concrete examples and game analogies (e.g. RuneScape bank tabs, nested folders for JSON) work well.
- Go **one step at a time** when he's learning; don't dump many steps at once.
- Boilerplate (getters/constructors, DB code, HTTP plumbing) can just be written for him — but **walk him through what was written afterward** so he knows what's in his project.
- Be direct and concise; don't end every message with a question; don't over-praise.
- Never invent API fields or method names — check the actual JSON/docs.
- Suggest commit messages at milestones.
