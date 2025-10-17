package org.example.invaders.rocketblasters;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.invaders.rocketblasters.net.NetLink;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Launches two H2H game windows on the same PC and connects them
 * together over 127.0.0.1 so you can test Host (green) vs Client (red).
 *
 * Host window = Player 1 (GREEN)
 * Client window = Player 2 (RED)
 */
public class LocalH2HLauncher extends Application {

    private NetLink hostLink;
    private NetLink clientLink;

    @Override
    public void start(Stage hostStage) throws Exception {
        // ---------- Load HOST (Player 1 / Green) ----------
        FXMLLoader hLoader = new FXMLLoader(getClass().getResource(
                "/org/example/invaders/rocketblasters/H2HGameView.fxml"));
        Parent hostRoot = hLoader.load();
        H2HGameController hostCtrl = hLoader.getController();

        Scene hostScene = new Scene(hostRoot, 1460, 980);
        hostStage.setTitle("Rocket Blasters [HOST - Player 1 / Green]");
        hostStage.setScene(hostScene);
        hostStage.setMinWidth(1460);
        hostStage.setMinHeight(980);
        hostStage.setX(40);
        hostStage.setY(40);
        hostStage.show();

        // ---------- Load CLIENT (Player 2 / Red) ----------
        Stage clientStage = new Stage();
        FXMLLoader cLoader = new FXMLLoader(getClass().getResource(
                "/org/example/invaders/rocketblasters/H2HGameView.fxml"));
        Parent clientRoot = cLoader.load();
        H2HGameController clientCtrl = cLoader.getController();

        Scene clientScene = new Scene(clientRoot, 1460, 980);
        clientStage.setTitle("Rocket Blasters [CLIENT - Player 2 / Red]");
        clientStage.setScene(clientScene);
        clientStage.setMinWidth(1460);
        clientStage.setMinHeight(980);
        clientStage.setX(hostStage.getX() + 80);
        clientStage.setY(hostStage.getY() + 60);
        clientStage.show();

        // ---------- Wire local TCP connection (loopback) ----------
        // Create a loopback ServerSocket on an ephemeral port
        ServerSocket ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        int port = ss.getLocalPort();

        // Accept (host side) in a background thread
        final Socket[] hostSockBox = new Socket[1];
        Thread acceptThread = new Thread(() -> {
            try {
                hostSockBox[0] = ss.accept();
            } catch (IOException ignored) {
            }
        }, "H2H-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        // Connect client side
        Socket clientSock = new Socket("127.0.0.1", port);

        // Wait for accept to complete
        acceptThread.join();
        Socket hostSock = hostSockBox[0];

        // We don't need the server socket anymore
        try { ss.close(); } catch (Exception ignored) {}

        // Build NetLinks and start their receive loops
        hostLink = new NetLink(hostSock, hostCtrl, /*localId=*/1);
        clientLink = new NetLink(clientSock, clientCtrl, /*localId=*/2);

        hostCtrl.setNetLink(hostLink, 1);
        clientCtrl.setNetLink(clientLink, 2);

        new Thread(hostLink, "H2H-Net[HOST]").start();
        new Thread(clientLink, "H2H-Net[CLIENT]").start();

        // ---------- Clean shutdown on either window close ----------
        hostStage.setOnCloseRequest(e -> shutdown());
        clientStage.setOnCloseRequest(e -> shutdown());
    }

    private void shutdown() {
        try { if (hostLink != null) hostLink.sendExit(); } catch (Exception ignored) {}
        try { if (clientLink != null) clientLink.sendExit(); } catch (Exception ignored) {}
        try { if (hostLink != null) hostLink.close(); } catch (Exception ignored) {}
        try { if (clientLink != null) clientLink.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}
