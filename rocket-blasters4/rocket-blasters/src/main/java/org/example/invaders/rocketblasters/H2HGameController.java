package org.example.invaders.rocketblasters;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.invaders.rocketblasters.net.NetLink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Head-to-Head game controller (localId: 1 = GREEN/host, 2 = RED/client).
 * - Green ship:  /assets/imgi_207_ship9b.png
 * - Red ship:    /assets/imgi_262_spaceship.pod_.1.yellow_0.png
 */
public class H2HGameController {

    // ======== FXML ========
    @FXML private Canvas canvas;
    @FXML private Label lblP1, lblTimer, lblP2;
    @FXML private VBox  gameOverBox;
    @FXML private Button restartBtn, exitBtn;

    // ======== Render / Input ========
    private GraphicsContext g;
    private final Set<KeyCode> pressed = new HashSet<>();

    // ======== Networking ========
    private NetLink link;
    private int localId = 1; // 1 = host (green), 2 = client (red)
    private boolean iAmGreen() { return localId == 1; }
    private boolean iAmRed()   { return localId == 2; }

    // ======== Constants ========
    private static final double SPEED = 420.0;
    private static final int    MAX_HP = 5;
    private static final double B_SPD = 680.0;
    private static final double B_R = 4.0;
    private static final double FIRE_COOLDOWN = 0.15;
    private static final double FIXED_DT = 1.0 / 60.0;
    private static final double ROUND_SECONDS = 180.0;

    // ======== Ships (images) ========
    private Image greenShipImg;   // bottom
    private Image redShipImg;     // top

    // Green ship draw size + collision radius
    private double gW = 90, gH = 120;
    private double gHalfW = gW / 2, gHalfH = gH / 2;
    private double R_GREEN = Math.min(gW, gH) * 0.38;

    // Red ship draw size + collision radius
    private double rW = 84, rH = 112;
    private double rHalfW = rW / 2, rHalfH = rH / 2;
    private double R_RED = Math.min(rW, rH) * 0.38;

    // ======== Game state ========
    private double gx = 700, gy = 860, gvx = 0;  // green
    private int hpGreen = MAX_HP;

    private double rx = 700, ry =  60, rvx = 0;  // red
    private int hpRed   = MAX_HP;

    // Lerp target from remote
    private double remoteTargetX, remoteTargetY;
    private boolean remotePosValid = false;

    private static class Bullet {
        int owner; double x, y, vy;
        Bullet(int o, double x, double y, double vy){ this.owner=o; this.x=x; this.y=y; this.vy=vy; }
    }
    private final List<Bullet> bullets = new ArrayList<>();
    private double cdGreen = 0, cdRed = 0;

    // Timer (host authoritative)
    private double timeLeft = ROUND_SECONDS;
    private double timeBroadcastCooldown = 0.0;

    // Spawn / size fallback
    private boolean spawned = false;
    private double lastW = 1400, lastH = 900;

    // Loop
    private double acc = 0;
    private long lastNanos = 0;
    private AnimationTimer loop;

    // Result popup
    private Stage resultStage;

    // ======== INIT ========
    @FXML
    public void initialize() {
        g = canvas.getGraphicsContext2D();
        loadShips();

        Platform.runLater(() -> {
            if (canvas.getScene() != null) {
                canvas.getScene().setOnKeyPressed(e -> {
                    pressed.add(e.getCode());
                    if (e.getCode() == KeyCode.ESCAPE) onExit();
                    if (e.getCode() == KeyCode.ENTER)  onRestart();
                });
                canvas.getScene().setOnKeyReleased(e -> pressed.remove(e.getCode()));
                canvas.requestFocus();
            }
        });

        // Wire bottom-bar buttons here (no FXML onAction needed)
        if (restartBtn != null) {
            restartBtn.setOnAction(e -> onRestart());
        }
        if (exitBtn != null) {
            exitBtn.setOnAction(e -> onExit());
        }

        canvas.widthProperty().addListener((o, ov, nv)  -> spawned = false);
        canvas.heightProperty().addListener((o, ov, nv) -> spawned = false);
        if (gameOverBox != null) gameOverBox.setVisible(false);

        updateHpLabels();
        updateTimerLabel();
        startLoop();
    }

    private void loadShips() {
        try {
            greenShipImg = new Image(getClass().getResourceAsStream(
                    "/org/example/invaders/rocketblasters/assets/imgi_207_ship9b.png"));
        } catch (Exception e) {
            System.err.println("Green ship not found -> will draw circle");
        }
        try {
            redShipImg = new Image(getClass().getResourceAsStream(
                    "/org/example/invaders/rocketblasters/assets/imgi_262_spaceship.pod_.1.yellow_0.png"));
        } catch (Exception e) {
            System.err.println("Red ship not found -> will draw circle");
        }
    }

    public void setNetLink(NetLink link, int localId) {
        this.link = link;
        this.localId = localId;
    }

    // ======== LOOP ========
    private void startLoop() {
        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNanos == 0) lastNanos = now;
                double dt = (now - lastNanos) / 1e9;
                lastNanos = now;
                if (dt > 0.25) dt = 0.25;
                acc += dt;
                while (acc >= FIXED_DT) { tickFixed(FIXED_DT); acc -= FIXED_DT; }
                render();
            }
        };
        loop.start();
    }

    private void ensureSpawned() {
        if (spawned) return;
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 1) w = lastW; else lastW = w;
        if (h <= 1) h = lastH; else lastH = h;

        gx = w * 0.5; gy = h - (gHalfH + 20);
        rx = w * 0.5; ry = (rHalfH + 20);
        clampInside();
        spawned = true;
    }

    private void tickFixed(double dt) {
        ensureSpawned();

        if (hpGreen <= 0 || hpRed <= 0 || timeLeft <= 0) return;

        cdGreen = Math.max(0, cdGreen - dt);
        cdRed   = Math.max(0, cdRed   - dt);

        // Local controls
        if (iAmGreen()) {
            gvx = 0;
            if (pressed.contains(KeyCode.A) || pressed.contains(KeyCode.LEFT))  gvx -= SPEED;
            if (pressed.contains(KeyCode.D) || pressed.contains(KeyCode.RIGHT)) gvx += SPEED;
            if ((pressed.contains(KeyCode.SPACE) || pressed.contains(KeyCode.ENTER)) && cdGreen == 0) {
                fireLocal(1, gx, gy - gHalfH);
                cdGreen = FIRE_COOLDOWN;
            }
            gx += gvx * dt;
        } else {
            rvx = 0;
            if (pressed.contains(KeyCode.LEFT)  || pressed.contains(KeyCode.J)) rvx -= SPEED;
            if (pressed.contains(KeyCode.RIGHT) || pressed.contains(KeyCode.L)) rvx += SPEED;
            if ((pressed.contains(KeyCode.SPACE) || pressed.contains(KeyCode.ENTER)) && cdRed == 0) {
                fireLocal(2, rx, ry + rHalfH);
                cdRed = FIRE_COOLDOWN;
            }
            rx += rvx * dt;
        }

        // Apply remote smoothing
        if (remotePosValid) {
            double a = 0.18;
            if (iAmGreen()) { rx += (remoteTargetX - rx) * a; ry += (remoteTargetY - ry) * a; }
            else            { gx += (remoteTargetX - gx) * a; gy += (remoteTargetY - gy) * a; }
        }

        clampInside();
        updateBullets(dt);
        updateTimer(dt);
        updateTimerLabel();

        // Broadcast local position
        if (link != null) {
            if (iAmGreen()) link.sendPosition(1, gx, gy, gvx, 0);
            else            link.sendPosition(2, rx, ry, rvx, 0);
        }
    }

    private void clampInside() {
        double w = Math.max(2, canvas.getWidth()  > 1 ? canvas.getWidth()  : lastW);
        double h = Math.max(2, canvas.getHeight() > 1 ? canvas.getHeight() : lastH);

        gx = Math.max(gHalfW, Math.min(w - gHalfW, gx));
        gy = Math.max(gHalfH, Math.min(h - gHalfH, gy));

        rx = Math.max(rHalfW, Math.min(w - rHalfW, rx));
        ry = Math.max(rHalfH, Math.min(h - rHalfH, ry));
    }

    private void updateBullets(double dt) {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.y += b.vy * dt;

            if (b.y < -20 || b.y > canvas.getHeight() + 20) {
                bullets.remove(i);
                continue;
            }
            if (iAmGreen() && b.owner == 1) {
                if (hit(b.x, b.y, B_R, rx, ry, R_RED)) {
                    bullets.remove(i);
                    damageRed();
                    if (link != null) link.sendHit(2);
                }
            } else if (iAmRed() && b.owner == 2) {
                if (hit(b.x, b.y, B_R, gx, gy, R_GREEN)) {
                    bullets.remove(i);
                    damageGreen();
                    if (link != null) link.sendHit(1);
                }
            }
        }
    }

    private void updateTimer(double dt) {
        if (iAmGreen()) {
            timeLeft = Math.max(0, timeLeft - dt);
            timeBroadcastCooldown -= dt;
            if (timeBroadcastCooldown <= 0 && link != null) {
                link.sendTime(timeLeft);
                timeBroadcastCooldown = 0.25;
            }
            if (timeLeft <= 0) endByTime();
        }
    }

    private static boolean hit(double x1, double y1, double r1, double x2, double y2, double r2) {
        double dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy <= (r1 + r2) * (r1 + r2);
    }

    // ======== RENDER ========
    private void render() {
        ensureSpawned();
        g.setFill(Color.web("#0B1C2C"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Red (top)
        if (redShipImg != null) g.drawImage(redShipImg, rx - rHalfW, ry - rHalfH, rW, rH);
        else { g.setFill(Color.CRIMSON); g.fillOval(rx - R_RED, ry - R_RED, R_RED*2, R_RED*2); }

        // Green (bottom)
        if (greenShipImg != null) g.drawImage(greenShipImg, gx - gHalfW, gy - gHalfH, gW, gH);
        else { g.setFill(Color.LIME); g.fillOval(gx - R_GREEN, gy - R_GREEN, R_GREEN*2, R_GREEN*2); }

        // Bullets
        g.setFill(Color.WHITE);
        for (Bullet b : bullets) g.fillOval(b.x - B_R, b.y - B_R, B_R * 2, B_R * 2);

        updateHpLabels();
    }

    // ======== DAMAGE / END ========
    private void damageGreen() {
        if (--hpGreen <= 0) gameOver("Red wins!");
        else updateHpLabels();
    }
    private void damageRed() {
        if (--hpRed <= 0) gameOver("Green wins!");
        else updateHpLabels();
    }

    private void endByTime() {
        String msg = (hpGreen > hpRed) ? "Green wins (time)!" :
                (hpRed   > hpGreen) ? "Red wins (time)!"   : "Draw!";
        gameOver(msg);
    }

    private void gameOver(String msg) {
        if (loop != null) loop.stop();
        showResultDialog(msg);
    }

    // ======== UI helpers ========
    private void updateHpLabels() {
        if (lblP1 != null) lblP1.setText("Green HP: " + hpGreen);
        if (lblP2 != null) lblP2.setText("Red HP: " + hpRed);
    }

    private void updateTimerLabel() {
        if (lblTimer == null) return;
        int t = (int) Math.ceil(timeLeft);
        lblTimer.setText(String.format("%02d:%02d", Math.max(0, t/60), Math.max(0, t%60)));
    }

    // ======== SHOOT / NETWORK ========
    private void fireLocal(int id, double x, double y) {
        bullets.add(new Bullet(id, x, y, id == 1 ? -B_SPD : +B_SPD));
        if (link != null) link.sendShot(id, x, y);
    }

    public void onRemoteShot(int id, double x, double y) {
        bullets.add(new Bullet(id, x, y, id == 1 ? -B_SPD : +B_SPD));
    }

    public void onRemotePosition(int id, double x, double y, double vx, double vy) {
        if (id == localId) return; // ignore echoes
        remoteTargetX = x; remoteTargetY = y; remotePosValid = true;
    }

    public void onRemoteHit(int targetId) {
        if (targetId == 1) damageGreen(); else damageRed();
    }

    public void onRemoteTime(double secondsLeft) {
        if (iAmRed()) {
            timeLeft = Math.max(0, secondsLeft);
            if (timeLeft <= 0) endByTime();
        }
    }

    /** Peer requests a restart. */
    public void remoteRestart() {
        Platform.runLater(() -> {
            resetRound();
            if (canvas != null) canvas.requestFocus();
        });
    }

    /** Peer leaves -> go back to menu. */
    public void remoteExit() {
        Platform.runLater(() -> {
            try {
                if (loop != null) loop.stop();
                Stage stage = (Stage) canvas.getScene().getWindow();
                stage.setScene(MainApp.loadScene("/org/example/invaders/rocketblasters/Menu.fxml"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ======== Popup (Restart / Back to Menu) ========
    private void showResultDialog(String message) {
        Label title = new Label(message);
        title.setStyle("-fx-text-fill:white; -fx-font-size:22; -fx-font-weight:bold;");

        Button btnRestart = new Button("Restart");
        btnRestart.setOnAction(e -> {
            if (link != null) link.sendRestart();
            resetRound();
            resultStage.close();
            if (canvas != null) canvas.requestFocus();
        });

        Button btnMenu = new Button("Back to Menu");
        btnMenu.setOnAction(e -> {
            if (link != null) link.sendExit();
            Platform.runLater(() -> {
                try {
                    Stage current = (Stage) canvas.getScene().getWindow();
                    current.setScene(MainApp.loadScene("/org/example/invaders/rocketblasters/Menu.fxml"));
                } catch (Exception ignored) {}
                resultStage.close();
            });
        });

        HBox buttons = new HBox(12, btnRestart, btnMenu);
        buttons.setStyle("-fx-alignment:center;");

        VBox root = new VBox(18, title, buttons);
        root.setStyle("-fx-padding:22; -fx-background-color:#202b3a;");
        resultStage = new Stage();
        resultStage.setScene(new Scene(root));
        resultStage.initModality(Modality.WINDOW_MODAL);
        resultStage.initOwner(ownerWindow());
        resultStage.setResizable(false);
        resultStage.show();
    }

    private Window ownerWindow() {
        return canvas != null && canvas.getScene() != null ? canvas.getScene().getWindow() : null;
    }

    // ======== Reset round ========
    private void resetRound() {
        hpGreen = MAX_HP; hpRed = MAX_HP;
        bullets.clear(); pressed.clear();
        remotePosValid = false;
        timeLeft = ROUND_SECONDS;
        acc = 0; lastNanos = 0;
        spawned = false; // respawn at new canvas size if resized

        updateHpLabels(); updateTimerLabel();

        if (loop != null) loop.start();
    }

    // ======== Bottom bar handlers (also bound to keys) ========
    @FXML private void onRestart() {
        if (link != null) link.sendRestart();
        resetRound();
        if (canvas != null) canvas.requestFocus();
    }

    @FXML private void onExit() {
        if (link != null) link.sendExit();
        if (loop != null) loop.stop();
        try {
            Stage current = (Stage) canvas.getScene().getWindow();
            current.setScene(MainApp.loadScene("/org/example/invaders/rocketblasters/Menu.fxml"));
        } catch (Exception ignored) {}
    }
}
