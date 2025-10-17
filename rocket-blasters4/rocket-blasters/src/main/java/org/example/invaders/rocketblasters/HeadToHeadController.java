package org.example.invaders.rocketblasters;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.example.invaders.rocketblasters.net.NetLink;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Controller for the Head-to-Head (Online) setup screen.
 * This screen shows options to HOST or JOIN a match before launching the H2H game view.
 */
public class HeadToHeadController {

    @FXML private TextField portField;
    @FXML private TextField hostField;
    @FXML private TextField joinPortField;
    @FXML private Label status;
    @FXML private Button backBtn;

    private static final int DEFAULT_PORT = 5555;

    // ==== When player chooses HOST ====
    @FXML
    private void onHost() {
        int port = readInt(portField, DEFAULT_PORT);
        setStatus("Starting host on port " + port + "...");

        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port, 1, InetAddress.getByName("0.0.0.0"))) {
                setStatus("Waiting for client to connect on port " + port + "...");
                Socket client = ss.accept();
                setStatus("Client connected: " + client.getInetAddress().getHostAddress());

                Platform.runLater(() -> launchMatch(client, /*localId=*/1)); // Host = green
            } catch (IOException e) {
                setStatus("Host error: " + e.getMessage());
            }
        }, "H2H-Host").start();
    }

    // ==== When player chooses JOIN ====
    @FXML
    private void onJoin() {
        String host = readText(hostField, "127.0.0.1");
        int port = (joinPortField != null && !isEmpty(joinPortField))
                ? readInt(joinPortField, DEFAULT_PORT)
                : readInt(portField, DEFAULT_PORT);

        setStatus("Connecting to " + host + ":" + port + "...");

        new Thread(() -> {
            try {
                Socket sock = new Socket(host, port);
                setStatus("Connected to host!");
                Platform.runLater(() -> launchMatch(sock, /*localId=*/2)); // Client = red
            } catch (IOException e) {
                setStatus("Join error: " + e.getMessage());
            }
        }, "H2H-Join").start();
    }

    // ==== Go back to main menu ====
    @FXML
    private void onBackToMenu() {
        MainApp.setRoot("/org/example/invaders/rocketblasters/Menu.fxml");
    }

    // ==== Launch the real battle scene ====
    private void launchMatch(Socket socket, int localId) {
        try {
            H2HGameController ctrl = MainApp.loadAndSet(
                    "/org/example/invaders/rocketblasters/H2HGameView.fxml",
                    H2HGameController.class);

            if (ctrl == null) {
                setStatus("Failed to load H2HGameView.fxml");
                socket.close();
                return;
            }

            NetLink link = new NetLink(socket, ctrl, localId);
            ctrl.setNetLink(link, localId);

            Thread t = new Thread(link, localId == 1 ? "H2H-Net[HOST]" : "H2H-Net[CLIENT]");
            t.setDaemon(true);
            t.start();

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Launch error: " + e.getMessage());
        }
    }

    // ==== Utility ====
    private void setStatus(String msg) {
        if (status != null) Platform.runLater(() -> status.setText("Status: " + msg));
        System.out.println("[H2H] " + msg);
    }

    private static boolean isEmpty(TextField tf) {
        return tf == null || tf.getText() == null || tf.getText().trim().isEmpty();
    }

    private static String readText(TextField tf, String def) {
        if (tf == null) return def;
        String s = tf.getText();
        return (s == null || s.trim().isEmpty()) ? def : s.trim();
    }

    private static int readInt(TextField tf, int def) {
        if (tf == null) return def;
        try {
            return Integer.parseInt(tf.getText().trim());
        } catch (Exception e) {
            return def;
        }
    }
}
