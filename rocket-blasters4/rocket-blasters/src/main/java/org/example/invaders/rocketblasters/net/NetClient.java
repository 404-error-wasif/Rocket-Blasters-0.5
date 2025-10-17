package org.example.invaders.rocketblasters.net;

import java.net.Socket;
import java.util.function.Consumer;

/**
 * Connects to host:port and calls onConnected with the connected Socket.
 */
public class NetClient {
    private final String host;
    private final int port;
    private final Consumer<Socket> onConnected;
    private Thread thread;

    public NetClient(String host, int port, Consumer<Socket> onConnected) {
        this.host = host;
        this.port = port;
        this.onConnected = onConnected;
    }

    public void start() {
        thread = new Thread(() -> {
            try {
                Socket s = new Socket(host, port);        // blocks until connected (or throws)
                onConnected.accept(s);
            } catch (Exception e) {
                throw new RuntimeException("Join error: " + e.getMessage(), e);
            }
        }, "H2H-Join");
        thread.setDaemon(true);
        thread.start();
    }
}
