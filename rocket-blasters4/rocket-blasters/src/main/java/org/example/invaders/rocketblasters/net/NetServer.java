package org.example.invaders.rocketblasters.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Listens on a TCP port and accepts exactly one client, then calls onAccept with the connected Socket.
 */
public class NetServer {
    private final int port;
    private final Consumer<Socket> onAccept;
    private Thread thread;

    public NetServer(int port, Consumer<Socket> onAccept) {
        this.port = port;
        this.onAccept = onAccept;
    }

    public void start() {
        thread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                Socket client = server.accept();          // blocks until client connects
                onAccept.accept(client);
            } catch (IOException e) {
                throw new RuntimeException("Host error: " + e.getMessage(), e);
            }
        }, "H2H-Host");
        thread.setDaemon(true);
        thread.start();
    }
}
