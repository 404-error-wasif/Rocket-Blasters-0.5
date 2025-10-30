package org.example.invaders.rocketblasters;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Objects;

import org.example.invaders.rocketblasters.util.GameMode;

/**
 * App entry + scene utilities (robust).
 * - Accepts both logical names ("GameView") and absolute resource paths ("/org/.../GameView.fxml").
 * - setRoot(...) works even if the shared Scene isn't initialized yet.
 * - Keeps your preferred window size and min-size.
 */
public class MainApp extends Application {

    public static Stage PRIMARY_STAGE;                // global stage (set in start)
    private static Scene scene;// shared scene for simple swaps
    public static GameMode currentGameMode = GameMode.SINGLE_PLAYER;

    private static final double PREF_W = 1460;
    private static final double PREF_H = 980;
    private static final String RES_BASE = "/org/example/invaders/rocketblasters/";

    // ---- lifecycle ----------------------------------------------------------

    @Override
    public void start(Stage stage) throws Exception {
        PRIMARY_STAGE = stage;

        Parent root = FXMLLoader.load(
                Objects.requireNonNull(MainApp.class.getResource(RES_BASE + "SplashView.fxml")));

        scene = new Scene(root, PREF_W, PREF_H);
        stage.setTitle("Rocket Blasters");
        stage.setScene(scene);
        stage.setMinWidth(PREF_W);
        stage.setMinHeight(PREF_H);
        stage.centerOnScreen();
        stage.setFullScreen(true); // Make the stage full-screen
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ---- helpers ------------------------------------------------------------

    /** Normalize "GameView" → "/org/.../GameView.fxml", or pass through an absolute "/... .fxml" path. */
    private static String normalize(String fxmlPath) {
        if (fxmlPath == null || fxmlPath.isBlank()) {
            return RES_BASE + "Menu.fxml";
        }
        String p = fxmlPath.trim();
        if (!p.startsWith("/")) {
            // treat as logical name with/without ".fxml"
            if (!p.endsWith(".fxml")) p = p + ".fxml";
            p = RES_BASE + p;
        }
        return p;
    }

    /** Ensure we have a Scene to swap roots on. Called by setRoot/loadAndSet when needed. */
    private static void ensureScene(Parent root) {
        if (scene == null) {
            Stage stage = PRIMARY_STAGE != null ? PRIMARY_STAGE : new Stage();
            scene = new Scene(root, PREF_W, PREF_H);
            stage.setScene(scene);
            stage.setMinWidth(PREF_W);
            stage.setMinHeight(PREF_H);
            if (PRIMARY_STAGE == null) {
                PRIMARY_STAGE = stage;
                stage.show();
            }
        }
    }

    /** Swap to a new root on the shared Scene; safe even if scene wasn't created yet. */
    public static void setRoot(String fxmlPath) {
        try {
            String path = normalize(fxmlPath);
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(path));
            Parent root = loader.load();

            if (scene == null) {
                ensureScene(root);
            } else {
                scene.setRoot(root);
                if (scene.getWindow() instanceof Stage s) {
                    s.setMinWidth(PREF_W);
                    s.setMinHeight(PREF_H);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Load an FXML and return a Scene; caller decides which Stage to set it on. */
    public static Scene loadScene(String fxmlPath) throws IOException {
        String path = normalize(fxmlPath);
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(path));
        Parent root = loader.load();
        return new Scene(root, PREF_W, PREF_H);
    }

    /**
     * Load FXML, set it as the shared Scene root, and return the controller (or null on failure).
     * Works even if scene isn't initialized yet.
     */
    public static <T> T loadAndSet(String fxmlPath, Class<T> controllerType) {
        try {
            String path = normalize(fxmlPath);
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(path));
            Parent root = loader.load();

            if (scene == null) {
                ensureScene(root);
            } else {
                scene.setRoot(root);
                if (scene.getWindow() instanceof Stage s) {
                    s.setMinWidth(PREF_W);
                    s.setMinHeight(PREF_H);
                }
            }
            Object ctrl = loader.getController();
            return controllerType.cast(ctrl);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Optional accessors (handy if other classes need them)
    public static Stage getPrimaryStage() { return PRIMARY_STAGE; }
    public static Scene getScene()       { return scene; }
}
