package org.example.invaders.rocketblasters;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.example.invaders.rocketblasters.util.GameMode;

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
        MainApp.currentGameMode = GameMode.SINGLE_PLAYER;
        System.out.println("DEBUG: Set game mode to SINGLE_PLAYER");
        MainApp.setRoot("/org/example/invaders/rocketblasters/GameView.fxml");
    }

    @FXML
    private void onHeadToHead() {
        MainApp.currentGameMode = GameMode.HEAD_TO_HEAD;
        MainApp.setRoot("/org/example/invaders/rocketblasters/BattleView.fxml");
    }

    @FXML
    private void onMultiplayerRanked() {
        MainApp.currentGameMode = GameMode.MULTIPLAYER_RANKED;
        System.out.println("DEBUG: Set game mode to MULTIPLAYER_RANKED");
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
