package org.example.invaders.rocketblasters;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.invaders.rocketblasters.util.Leaderboard;

import java.util.List;

public class LeaderboardController {
    @FXML private TableView<String[]> table;
    @FXML private TableColumn<String[], String> colRank;
    @FXML private TableColumn<String[], String> colName;
    @FXML private TableColumn<String[], String> colScore;

    @FXML
    public void initialize() {
        colRank.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[0]));
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[1]));
        colScore.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[2]));

        List<String> lines = Leaderboard.topN(20);
        int rank = 1;
        var data = FXCollections.<String[]>observableArrayList();
        for (String line : lines) {
            String[] p = line.split(",");
            if (p.length >= 2)
                data.add(new String[]{String.valueOf(rank++), p[0], p[1]});
        }
        table.setItems(data);
    }

    @FXML
    private void onBackToMenu() {
        MainApp.setRoot("/org/example/invaders/rocketblasters/Menu.fxml");
    }
}
