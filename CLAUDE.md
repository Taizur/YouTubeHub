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
- Future: JavaFX

## Auth decision

Using an **API key only** (public data), no OAuth for now. OAuth would only be needed later for pulling the real subscription list or syncing playlists to the YouTube account. Note: OAuth apps left in "testing" mode reportedly get refresh tokens that expire after ~7 days — verify before relying on it.

## Secrets and local files

- `config.properties` in the **project root** holds `youtube.api.key=...`. It is gitignored. **Never commit it, print it, or log the full request URL** (the key is in the query string).
- `youtubehub.db` (SQLite database, created automatically in the project root at runtime) is gitignored.
- Repo is on GitHub (private).

## Quota notes

- Free daily quota; no billing account needed.
- `videos`, `channels`, `playlistItems` list calls cost ~1 unit each. **`search` costs 100 — avoid it.**
- Get a channel's videos via its **uploads playlist** (`UU...` ID from `channels?part=contentDetails` → `contentDetails.relatedPlaylists.uploads`), then `playlistItems`. The uploads playlist ID is looked up once when a channel is added and **saved in the database** so it never needs re-fetching.

## Current code (all under `src/main/java/com/youtubehub/`)

| File | Status / purpose |
|---|---|
| `Main.java` | Test bench only. Currently loads config, initializes the DB, builds the podcast feed and prints it. |
| `config/AppConfig.java` | Loads `config.properties` with `Properties`; `load()`, `getApiKey()`. |
| `api/YouTubeClient.java` | Talks to YouTube. Private `get(endpointAndQuery)` helper adds base URL + key, sends request, throws `IOException` on non-200. Methods: `getVideoJson(id)`, `getVideo(id)` → `Video`, `getUploadsPlaylistId(channelId)` → `UU...` or null, `getPlaylistItemsJson(playlistId, maxResults)` (clamped to 1–50, the API limit), `getRecentUploads(channelId, maxResults)` → `List<Video>`, `getVideosFromPlaylist(playlistId, maxResults)` → `List<Video>`, **always drops live streams/VODs** (private `findStreamIds` does one `videos?part=liveStreamingDetails` call, 1 unit; a video with a `liveStreamingDetails` section is a stream). Brandon never wants VODs shown. It scans the newest 50 uploads (`UPLOADS_TO_SCAN`, same 1 unit), drops streams, then keeps the newest `maxResults`. Subscription feed uses 3 per channel, podcasts 5. |
| `model/Video.java` | `videoId`, `videoTitle`, `channelID`, `channelTitle`, `publishedAt` (all Strings; publishedAt is ISO-8601 like `2026-10-29T17:00:32Z`). Getters, plus `getPublishedInstant()` → `Instant` or null if blank. |
| `model/Channel.java` | `channelId`, `channelTitle`, `uploadsPlaylistId`, `priority` (int), `podcast` (boolean). Getters + `setPriority`, `setPodcast`. |
| `storage/ChannelRepository.java` | SQLite `channels` table. `initialize()`, `addChannel` (upsert), `getAllChannels`, `removeChannel`, `updatePriority`, `updatePodcast`. |
| `service/ChannelService.java` | `addChannel(channelId, priority, podcast)` — looks up title + uploads playlist via `YouTubeClient.getChannel(id)` (1 unit), saves to DB, returns the `Channel`; throws `IllegalArgumentException` for an unknown ID. The UI's "Add channel" will call this. |
| `storage/WatchedRepository.java` | SQLite `watched_videos` table (`video_id` PK, `watched_at` ISO text). `initialize()`, `markWatched(id)` (upsert, updates time), `markUnwatched(id)`, `isWatched(id)`, `getWatchedIds()` → `Set<String>`. |
| `service/FeedFilter.java` | `static newest(videos, limit, hideWatched, watchedIds)` — keeps the first `limit` videos, skipping watched ones before counting so channels still fill up. Used by both feeds. |
| `service/PodcastService.java` | `getPodcastFeed(videosPerChannel, hideWatched)` — all channels where `podcast == true`, fetch 50 from each saved uploads playlist, `FeedFilter.newest`, combine, sort newest first (string sort on ISO date). Constructor takes `ChannelRepository, WatchedRepository, YouTubeClient`. |
| `service/SubscriptionService.java` | `getSubscriptionFeed(videosPerChannel, hideWatched)` → `List<ScoredVideo>` (constructor: `ChannelRepository, WatchedRepository, YouTubeClient, hoursPerPriorityPoint`), non-podcast channels ranked by effective age: `realAgeHours - priority * hoursPerPriorityPoint` (lowest first). `hoursPerPriorityPoint` is a constructor arg (Main uses 24); later it becomes a UI setting. |
| `model/ScoredVideo.java` | Record: `video`, `priority`, `ageHours`, `effectiveAgeHours`. |
| `service/Music.java` | Empty placeholder. |

Saved test data: JRE (`UCzQUP1qoWDoEbmsQxvdjxgQ`, uploads `UUzQUP1qoWDoEbmsQxvdjxgQ`), priority 5, podcast = true.

Podcast vs subscription split: a single `podcast` boolean. Podcast feed = `podcast == true`, subscription feed = `podcast == false`. If a channel ever needs to be in both feeds, split into two booleans.

## Next steps (rough order)

1. ~~Make `maxResults` a parameter~~ (done). Still to consider: an age cutoff (e.g. last N days) as an addition.
2. Channels are never hardcoded: they get added through the UI (step 6) via `ChannelService.addChannel` (done). Main can call it temporarily to put test data in the DB.
3. ~~**Subscription feed service**~~ (done; tune `hoursPerPriorityPoint`). — non-podcast channels, ranked by a tunable priority/recency score. Per-channel priority is already in the DB; the overall weighting should become a user setting editable in the UI. Convert `publishedAt` to `Instant` for the math.
4. ~~Watched tracking~~ (done: `WatchedRepository` + `hideWatched` on both feeds). Still to do in the UI: show a watched marker when not hiding.
5. Open videos in a new Opera GX tab (configurable executable path), mark as watched on click.
6. JavaFX UI — Podcasts / Subscriptions / Music sections, channel management screen.
7. Music library and playlists (own tables; YouTube sync would need OAuth). **Music is song-based, not channel-based**: Brandon adds individual videos he likes (by video ID/URL), regardless of channel. Planned tables: `songs` (video ID, title, channel title, added timestamp — filled via `getVideo(id)`, 1 unit), `playlists` (id, name, e.g. "Metal"), `playlist_songs` (playlist id + video ID, links a song to any number of playlists). The master playlist = every row in `songs` (no separate table needed). Music does not use the `channels` table.

## How Brandon wants to work

- He's a CS student, about first-year level in practice. Comfortable with Java basics (classes, objects, loops, ArrayLists). Less familiar with exception handling, file paths, library APIs (Jackson, JDBC, HttpClient) and their syntax.
- Explain new concepts in **plain, non-technical language**. Concrete examples and game analogies (e.g. RuneScape bank tabs, nested folders for JSON) work well.
- Go **one step at a time** when he's learning; don't dump many steps at once.
- Boilerplate (getters/constructors, DB code, HTTP plumbing) can just be written for him — but **walk him through what was written afterward** so he knows what's in his project.
- Be direct and concise; don't end every message with a question; don't over-praise.
- Never invent API fields or method names — check the actual JSON/docs.
- Suggest commit messages at milestones.
