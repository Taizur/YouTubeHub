package com.youtubehub;

import com.youtubehub.ui.YouTubeHubApp;
import javafx.application.Application;

// Run THIS to start the app from IntelliJ (green arrow next to main).
// Why a separate class: if you run a class that extends Application directly from IntelliJ,
// Java refuses with "JavaFX runtime components are missing". Starting it from a plain class avoids that.
public class Launcher {
    public static void main(String[] args) {
        Application.launch(YouTubeHubApp.class, args);
    }
}
