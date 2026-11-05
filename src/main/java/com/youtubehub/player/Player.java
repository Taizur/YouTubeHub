package com.youtubehub.player;

import com.youtubehub.model.Video;
import com.youtubehub.service.VideoOpener;
import com.youtubehub.storage.WatchedRepository;

// The "brain" of autoplay. It remembers:
//   - which Opera tab is the player tab (the one the extension says is showing our video)
//   - the play queue and which video is current
//   - a video waiting to be sent to the tab ("pending")
//
// The UI calls play / skip / stop. The extension (through ExtensionServer) calls hello / poll / ended.
// Those come from different threads, so every method is `synchronized`: only one thread
// can be inside this object at a time, like only one player holding the ball.
public class Player {
    private static final long POLL_WAIT_MILLIS = 20_000; // under the extension's 30-second fetch limit
    private static final long CONNECTED_GRACE_MILLIS = 5_000;

    private final VideoOpener videoOpener;
    private final WatchedRepository watchedRepository;
    private Runnable onChange = () -> {};

    private PlayQueue queue;          // null = not autoplaying
    private Integer playerTab;        // Opera's id for the player tab; null = none yet
    private String expectedVideoId;   // the video we last asked to be shown
    private String pendingVideoId;    // waiting to be handed to the tab on its next poll
    private int pollsWaiting = 0;
    private long lastPollEnded = 0;

    public Player(VideoOpener videoOpener, WatchedRepository watchedRepository) {
        this.videoOpener = videoOpener;
        this.watchedRepository = watchedRepository;
    }

    // The UI passes something to run whenever the queue / now-playing / watched marks change.
    // It may be called from a server thread, so the UI wraps it in Platform.runLater.
    public synchronized void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    // ---------- called by the UI ----------

    public synchronized void play(PlayQueue newQueue) throws Exception {
        if (newQueue.isEmpty()) {
            return;
        }
        queue = newQueue;
        load(queue.current());
    }

    public synchronized void skip() throws Exception {
        if (queue == null) {
            return;
        }
        if (advanceToNextUnwatched()) {
            load(queue.current());
        } else {
            stop();
        }
    }

    // Goes back one video. Does nothing on the first video (the UI greys the button out there).
    public synchronized void previous() throws Exception {
        if (queue == null || !queue.back()) {
            return;
        }
        load(queue.current());
    }

    // True when there's a video before the current one (the UI greys out Previous otherwise)
    public synchronized boolean hasPrevious() {
        return queue != null && queue.position() > 1;
    }

    public synchronized void stop() {
        queue = null;
        pendingVideoId = null;
        onChange.run();
    }

    // Is an Opera tab checking in with us right now?
    // A poll is held open for up to 20 seconds, and the tab asks again straight away,
    // so "a poll is waiting, or one finished in the last few seconds" means yes.
    public synchronized boolean isConnected() {
        return playerTab != null
                && (pollsWaiting > 0 || System.currentTimeMillis() - lastPollEnded < CONNECTED_GRACE_MILLIS);
    }

    // "Now playing: Title (3 of 12)", or "" when not autoplaying.
    public synchronized String getStatusText() {
        if (queue == null) {
            return "";
        }
        String text = "Now playing: " + queue.current().getVideoTitle() + " (" + queue.position() + " of " + queue.size() + ")";
        if (!isConnected()) {
            text += " — waiting for the Opera tab";
        }
        return text;
    }

    public synchronized boolean isPlaying() {
        return queue != null;
    }

    // Moves the queue forward, skipping feed videos that got marked watched after the queue was made
    // (e.g. you right-clicked "Mark watched" while something else was playing). The queue is a copy of
    // the feed from when you pressed play, so without this it would still play them.
    // Music queues never skip anything (songs don't use watched).
    // Returns false when there's nothing left to play.
    private boolean advanceToNextUnwatched() throws Exception {
        while (queue.advance()) {
            if (!queue.marksWatched() || !watchedRepository.isWatched(queue.current().getVideoId())) {
                return true;
            }
        }
        return false;
    }

    // Shows a video: in the player tab if there is one, otherwise in a new Opera tab.
    private void load(Video video) throws Exception {
        String videoId = video.getVideoId();
        expectedVideoId = videoId;
        if (isConnected()) {
            pendingVideoId = videoId;
            notifyAll(); // wakes up the waiting poll() so it can hand the video over right now
        } else {
            pendingVideoId = null;
            videoOpener.openWithoutMarking(videoId); // fallback: the new tab will say hello and become the player
        }
        if (queue != null && queue.marksWatched()) {
            watchedRepository.markWatched(videoId);
        }
        onChange.run();
    }

    // ---------- called by the extension, through ExtensionServer ----------

    // A YouTube watch page just loaded in `tab`. Returns true if that tab is the player tab.
    public synchronized boolean hello(int tab, String videoId) {
        boolean isOurVideo = videoId != null && videoId.equals(expectedVideoId);
        if (isOurVideo) {
            playerTab = tab;
        }
        if (videoId != null && videoId.equals(pendingVideoId)) {
            pendingVideoId = null; // it arrived
        }
        onChange.run();
        return playerTab != null && playerTab == tab;
    }

    // The player tab asks "anything for me?". Waits up to 20 seconds for an answer.
    // Returns the video to load, "" for nothing, or null if this tab isn't the player.
    public synchronized String poll(int tab) throws InterruptedException {
        if (playerTab == null) {
            playerTab = tab; // e.g. the app was restarted while the tab stayed open
        }
        if (playerTab != tab) {
            return null;
        }
        pollsWaiting++;
        try {
            long deadline = System.currentTimeMillis() + POLL_WAIT_MILLIS;
            while (pendingVideoId == null && playerTab == tab) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    break;
                }
                wait(left); // lets go of the lock while sleeping, so other methods can run
            }
            if (playerTab != tab) {
                return null;
            }
            // Not cleared here: if the page navigates before the answer arrives, the next poll
            // gets it again. hello() clears it once the video has really loaded.
            return pendingVideoId == null ? "" : pendingVideoId;
        } finally {
            pollsWaiting--;
            lastPollEnded = System.currentTimeMillis();
        }
    }

    // The video in `tab` finished. If it's the current queue video, move on.
    // Returns the next video to load, or "" for nothing.
    public synchronized String ended(int tab, String videoId) throws Exception {
        if (queue == null || playerTab == null || playerTab != tab
                || videoId == null || !videoId.equals(queue.current().getVideoId())) {
            return "";
        }
        if (!advanceToNextUnwatched()) {
            stop(); // that was the last one
            return "";
        }
        load(queue.current());
        return queue.current().getVideoId();
    }
}
