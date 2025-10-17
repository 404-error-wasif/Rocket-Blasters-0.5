package org.example.invaders.rocketblasters;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MenuController {

    @FXML private Label bestMenuLabel; // fx:id="bestMenuLabel" in Menu.fxml

    @FXML
    private void initialize() {
        // Refresh best score every time the menu is shown
        int best = loadBestFromFile();
        if (bestMenuLabel != null) {
            bestMenuLabel.setText("Best: " + best);
        }
    }

    // ---------- Navigation ----------
    @FXML
    private void onSinglePlayer() {
        MainApp.setRoot("/org/example/invaders/rocketblasters/GameView.fxml");
    }

    @FXML
    private void onHeadToHead() {
        MainApp.setRoot("/org/example/invaders/rocketblasters/BattleView.fxml");
    }

    @FXML
    private void onMultiplayerRanked() {
        // Point to your ranked/multiplayer scene if/when you have it
        MainApp.setRoot("/org/example/invaders/rocketblasters/LeaderboardView.fxml");
    }

    @FXML
    private void onLeaderboard() {
        MainApp.setRoot("/org/example/invaders/rocketblasters/LeaderboardView.fxml");
    }

    @FXML
    private void onQuit() {
        System.exit(0);
    }

    // ---------- Local best-score storage (read-only here) ----------
    private int loadBestFromFile() {
        File f = new File("bestscore.txt");
        if (!f.exists()) return 0;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            return Math.max(0, Integer.parseInt(line == null ? "0" : line.trim()));
        } catch (Exception e) {
            // If file is corrupt or unreadable, just show 0
            return 0;
        }
    }
}
