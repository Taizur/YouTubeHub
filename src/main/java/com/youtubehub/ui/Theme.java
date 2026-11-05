package com.youtubehub.ui;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;

// Puts app.css (src/main/resources/com/youtubehub/ui/app.css) on a window.
// The main window gets it once. Every popup (error, confirm, "New playlist") is its own little
// window, so it needs it too: call Theme.apply(dialog) before showing one.
public final class Theme {
    // getResource looks next to this class, so "app.css" = com/youtubehub/ui/app.css in resources.
    private static final String CSS = Theme.class.getResource("app.css").toExternalForm();

    private Theme() {
    }

    public static void apply(Scene scene) {
        scene.getStylesheets().add(CSS);
    }

    public static void apply(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(CSS);
    }
}
