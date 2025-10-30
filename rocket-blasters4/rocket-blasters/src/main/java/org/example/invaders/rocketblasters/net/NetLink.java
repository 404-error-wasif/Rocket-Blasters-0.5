package org.example.invaders.rocketblasters.net;

import javafx.application.Platform;
import org.example.invaders.rocketblasters.H2HGameController;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Very small line-oriented protocol over a TCP socket.
 * Lines we send/receive:
 *   POS <id> <x> <y> <vx> <vy>
 *   SHOT <id> <x> <y>
 *   HIT <targetId>
 *   TIME <secondsLeft>      // NEW – host -> client for clock sync
 *   RESTART
 *   EXIT
 */
public class NetLink implements Runnable {

    private final Socket socket;
    private final H2HGameController controller;
    private final int localId;

    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean running = true;

    public NetLink(Socket socket, H2HGameController controller, int localId) {
        this.socket = socket;
        this.controller = controller;
        this.localId = localId;
        try {
            this.in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            throw new RuntimeException("NetLink IO init failed: " + e.getMessage(), e);
        }
    }


    /** Low-level helper used by all send* methods. */
    private synchronized void sendRaw(String line) {
        if (!running) return;
        out.println(line);
        // out.flush(); // PrintWriter with auto-flush on println, but OK to leave as is
    }

    public void sendPosition(int id, double x, double y, double vx, double vy) {
        sendRaw("POS " + id + " " + x + " " + y + " " + vx + " " + vy);
    }

    public void sendShot(int id, double x, double y) {
        sendRaw("SHOT " + id + " " + x + " " + y);
    }

    public void sendHit(int targetId) {
        sendRaw("HIT " + targetId);
    }

    /** Host uses this to broadcast timer to client. */
    public void sendTime(double secondsLeft) {
        sendRaw("TIME " + secondsLeft);
    }

    public void sendRestart() {
        sendRaw("RESTART");
    }

    public void sendExit() {
        sendRaw("EXIT");
    }

    // ===== receive loop =====

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] tok = line.split("\\s+");
                String cmd = tok[0];

                switch (cmd) {
                    case "POS": { // POS <id> <x> <y> <vx> <vy>
                        if (tok.length >= 6) {
                            int id = parseInt(tok[1], -1);
                            double x = parseDouble(tok[2], 0);
                            double y = parseDouble(tok[3], 0);
                            double vx = parseDouble(tok[4], 0);
                            double vy = parseDouble(tok[5], 0);
                            Platform.runLater(() -> controller.onRemotePosition(id, x, y, vx, vy));
                        }
                        break;
                    }
                    case "SHOT": { // SHOT <id> <x> <y>
                        if (tok.length >= 4) {
                            int id = parseInt(tok[1], -1);
                            double x = parseDouble(tok[2], 0);
                            double y = parseDouble(tok[3], 0);
                            Platform.runLater(() -> controller.onRemoteShot(id, x, y));
                        }
                        break;
                    }
                    case "HIT": { // HIT <targetId>
                        if (tok.length >= 2) {
                            int target = parseInt(tok[1], -1);
                            Platform.runLater(() -> controller.onRemoteHit(target));
                        }
                        break;
                    }
                    case "TIME": { // TIME <secondsLeft>
                        if (tok.length >= 2) {
                            double secs = parseDouble(tok[1], 0);
                            Platform.runLater(() -> controller.onRemoteTime(secs));
                        }
                        break;
                    }
                    case "RESTART": {
                        Platform.runLater(controller::remoteRestart);
                        break;
                    }
                    case "EXIT": {
                        Platform.runLater(controller::remoteExit);
                        close();
                        break;
                    }
                    default:
                        // ignore unknown lines
                }
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    // ===== utils =====

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    public void close() {
        running = false;
        try { socket.close(); } catch (Exception ignored) {}
    }
}
