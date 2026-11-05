package com.youtubehub.player;

import com.youtubehub.model.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// The list of videos the player works through, one after another.
// It's a copy: refreshing or re-filtering the feeds afterwards doesn't change it.
public class PlayQueue {
    private final List<Video> videos;
    private final boolean markWatched; // feeds: yes. Music: no.
    private int index = 0;

    private PlayQueue(List<Video> videos, boolean markWatched) {
        this.videos = videos;
        this.markWatched = markWatched;
    }

    // Feeds: start at `startIndex` and carry on down the list in the order shown.
    public static PlayQueue inOrder(List<Video> list, int startIndex, boolean markWatched) {
        return new PlayQueue(new ArrayList<>(list.subList(startIndex, list.size())), markWatched);
    }

    // Music: a fresh random order every time. If `first` is given (you double-clicked a song),
    // it plays first and the rest are shuffled behind it.
    public static PlayQueue shuffled(List<Video> list, Video first) {
        List<Video> copy = new ArrayList<>(list);
        Collections.shuffle(copy);
        if (first != null) {
            copy.remove(first);
            copy.add(0, first);
        }
        return new PlayQueue(copy, false);
    }

    public boolean isEmpty() {
        return videos.isEmpty();
    }

    public Video current() {
        return videos.get(index);
    }

    // Moves to the next video. Returns false when there are none left.
    public boolean advance() {
        if (index + 1 >= videos.size()) {
            return false;
        }
        index++;
        return true;
    }

    // Moves back one video. Returns false if already at the first one.
    public boolean back() {
        if (index == 0) {
            return false;
        }
        index--;
        return true;
    }

    public int position() {
        return index + 1; // 1-based, for "3 of 12"
    }

    public int size() {
        return videos.size();
    }

    public boolean marksWatched() {
        return markWatched;
    }
}
