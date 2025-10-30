package org.example.invaders.rocketblasters;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane; // Import StackPane
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import java.net.URL;
import java.util.Objects;

public class SplashController {

    @FXML
    private MediaView mediaView; // Injected from FXML
    @FXML
    private StackPane rootPane; // Injected root pane

    private MediaPlayer mediaPlayer;

    private static final String VIDEO_PATH = "/org/example/invaders/rocketblasters/assets/video/Video_Generation_Request.mp4"; // Corrected path based on previous examples

    @FXML
    public void initialize() {
        // Bind MediaView size to its parent pane size BEFORE loading media

        try {
            URL videoUrl = getClass().getResource(VIDEO_PATH);
            if (videoUrl == null) {
                System.err.println("ERROR: Splash Video not found at path: " + VIDEO_PATH);
                navigateToMenu();
                return;
            }

            Media media = new Media(videoUrl.toExternalForm());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setMute(false);
            mediaPlayer.setVolume(0.8);
            mediaPlayer.setAutoPlay(true);

            mediaPlayer.setOnEndOfMedia(this::navigateToMenu);
            mediaPlayer.setOnError(() -> {
                System.err.println("MediaPlayer Error: " + mediaPlayer.getError().getMessage());
                navigateToMenu();
            });
            media.setOnError(() -> {
                System.err.println("Media Error: " + media.getError().getMessage());
                navigateToMenu();
            });

        } catch (Exception e) {
            System.err.println("Failed to initialize splash screen video.");
            e.printStackTrace();
            navigateToMenu();
        }
    }

    private void navigateToMenu() {
        javafx.application.Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }
            MainApp.setRoot("Menu");
        });
    }
}