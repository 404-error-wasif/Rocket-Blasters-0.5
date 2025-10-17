package org.example.invaders.rocketblasters;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;

import java.io.*;
import java.net.URL;
import java.util.*;

public class GameController {

    // ===== FXML =====
    @FXML private Canvas canvas;
    @FXML private Label scoreLabel;
    @FXML private Label livesLabel;
    @FXML private Label bestLabel;

    // ===== Render/Input =====
    private GraphicsContext g;
    private final Set<KeyCode> keys = new HashSet<>();
    private AnimationTimer loop;
    private boolean running = false;

    // ===== Background video =====
    private MediaPlayer bgPlayer;
    private boolean videoOk = false;          // if false, we draw a starfield fallback

    // ===== Player (image ship) =====
    private double px, py, pvx, pvy;
    private static final double P_SPEED = 400;

    private Image shipImg;
    private double shipW = 90, shipH = 120;
    private double shipHalfW = shipW / 2.0, shipHalfH = shipH / 2.0;
    private double P_R = Math.min(shipW, shipH) * 0.38;

    // ===== Bullets =====
    private static final double B_R = 4;
    private static final double B_SPD = 700;
    private static final double FIRE_CD = 0.15;
    private double fireCooldown = 0.0;
    private static class Bullet { double x,y,vy; Bullet(double x,double y,double vy){this.x=x;this.y=y;this.vy=vy;} }
    private final List<Bullet> bullets = new ArrayList<>();

    // ===== Enemies (rocket sprites + pulsing glow) =====
    private static final double E_R = 14;                 // collision radius
    private final List<double[]> enemies = new ArrayList<>(); // {x,y,vx,vy}
    private double spawnTimer = 0;
    private Image enemyImg;                               // sprite used for enemies
    // draw size for enemy rockets
    private double enemyW = 72, enemyH = 96;
    private double enemyHalfW = enemyW/2.0, enemyHalfH = enemyH/2.0;

    // Glow animation phase (seconds)
    private double glowPhase = 0.0;

    // ===== Score/lives/best =====
    private int score = 0;
    private int lives = 3;
    private int best  = 0;

    // ===== Time =====
    private long lastNs = 0;

    // ===== Fallback starfield state =====
    private final Random rnd = new Random();
    private static class Star { double x,y,s; }
    private final List<Star> stars = new ArrayList<>();
    private double starSpeed = 180; // px/sec “forward motion”

    @FXML
    public void initialize() {
        g = canvas.getGraphicsContext2D();
        loadShipImage();
        loadEnemyImage();       // <--- load enemy rocket sprite
        setupBackgroundVideo();
        loadBest();
        updateHud(); // show initial values

        Platform.runLater(() -> {
            if (canvas.getScene() != null) {
                canvas.getScene().setOnKeyPressed(e -> {
                    keys.add(e.getCode());
                    if (e.getCode() == KeyCode.ESCAPE) onBackToMenu();
                    if (e.getCode() == KeyCode.ENTER)  onRestart();
                });
                canvas.getScene().setOnKeyReleased(e -> keys.remove(e.getCode()));
                canvas.requestFocus();
                startSinglePlayer();
            }
        });

        canvas.widthProperty().addListener((o,ov,nv)->{
            clampPlayer();
            if (!videoOk) initStars();
        });
        canvas.heightProperty().addListener((o,ov,nv)->{
            clampPlayer();
            if (!videoOk) initStars();
        });
    }

    private void loadShipImage() {
        try {
            shipImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(
                            "/org/example/invaders/rocketblasters/assets/imgi_207_ship9b.png")));
        } catch (Exception ex) {
            System.err.println("Could not load player ship image; falling back to circle.");
            shipImg = null;
        }
    }

    private void loadEnemyImage() {
        // Use your chosen enemy sprite (you can change this path)
        final String enemyPath = "/org/example/invaders/rocketblasters/assets/imgi_127_dahlia-b-ccp2.gif";
        try {
            enemyImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(enemyPath)));
        } catch (Exception ex) {
            System.err.println("Could not load enemy rocket image (" + enemyPath + "); falling back to circle.");
            enemyImg = null;
        }
    }

    // === Background video (with diagnostics + onReady play) ===
    private void setupBackgroundVideo() {
        final String rel = "/org/example/invaders/rocketblasters/assets/video/space_loop.mp4";
        try {
            URL url = getClass().getResource(rel);
            if (url == null) {
                System.err.println("[Video] Resource not found on classpath: " + rel);
                videoOk = false;
                initStars();
                return;
            }
            Media media = new Media(url.toExternalForm());
            media.setOnError(() -> System.err.println("[Video] Media error: " + media.getError()));

            bgPlayer = new MediaPlayer(media);
            bgPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            bgPlayer.setAutoPlay(false);   // play on READY
            bgPlayer.setMute(true);

            bgPlayer.setOnError(() -> System.err.println("[Video] Player error: " + bgPlayer.getError()));
            bgPlayer.setOnReady(() -> {
                try { bgPlayer.play(); } catch (MediaException ex) { System.err.println("[Video] play() failed: " + ex); }
            });
            bgPlayer.setOnPlaying(() -> videoOk = true);

            MediaView mv = new MediaView(bgPlayer);
            mv.setPreserveRatio(true);
            mv.setMouseTransparent(true);
            mv.fitWidthProperty().bind(canvas.widthProperty());
            mv.fitHeightProperty().bind(canvas.heightProperty());

            if (canvas.getParent() instanceof StackPane sp) {
                sp.getChildren().add(0, mv); // behind the Canvas
            } else {
                System.err.println("[Video] Canvas parent is not a StackPane; background may not show behind canvas.");
            }
        } catch (Throwable ex) {
            System.err.println("[Video] Failed to init video: " + ex);
            videoOk = false;
            initStars();
        }
    }

    private void stopBackgroundVideo() {
        try { if (bgPlayer != null) { bgPlayer.stop(); bgPlayer.dispose(); } }
        catch (Exception ignored) {}
    }

    // === Single player ===
    public void startSinglePlayer() {
        if (running) return;
        resetGame();

        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNs == 0) lastNs = now;
                double dt = (now - lastNs) / 1e9;
                if (dt > 0.25) dt = 0.25;
                lastNs = now;

                tick(dt);
                render(dt);
            }
        };
        running = true;
        loop.start();
    }

    private void resetGame() {
        score = 0; lives = 3;
        bullets.clear(); enemies.clear();
        fireCooldown = 0; spawnTimer = 0; lastNs = 0;

        double w = Math.max(600, canvas.getWidth());
        double h = Math.max(400, canvas.getHeight());
        px = w * 0.5; py = h - 100; pvx = 0; pvy = 0;

        if (!videoOk) initStars();
        updateHud();
    }

    private void tick(double dt) {
        // movement (WASD or arrows)
        pvx = 0; pvy = 0;
        if (keys.contains(KeyCode.A) || keys.contains(KeyCode.LEFT))  pvx -= P_SPEED;
        if (keys.contains(KeyCode.D) || keys.contains(KeyCode.RIGHT)) pvx += P_SPEED;
        if (keys.contains(KeyCode.W) || keys.contains(KeyCode.UP))    pvy -= P_SPEED;
        if (keys.contains(KeyCode.S) || keys.contains(KeyCode.DOWN))  pvy += P_SPEED;

        boolean shoot = keys.contains(KeyCode.SPACE) || keys.contains(KeyCode.ENTER);

        px += pvx * dt;
        py += pvy * dt;
        clampPlayer();

        // bullets
        fireCooldown = Math.max(0, fireCooldown - dt);
        if (shoot && fireCooldown == 0.0) {
            bullets.add(new Bullet(px, py - shipHalfH, -B_SPD)); // from nose
            fireCooldown = FIRE_CD;
        }

        // bullets vs enemies
        for (int i = bullets.size()-1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.y += b.vy * dt;
            if (b.y < -10) { bullets.remove(i); continue; }
            for (int j = enemies.size()-1; j >= 0; j--) {
                double[] e = enemies.get(j); // x,y,vx,vy
                if (hit(b.x, b.y, B_R, e[0], e[1], E_R)) {
                    bullets.remove(i);
                    enemies.remove(j);
                    score += 10;
                    if (score > best) best = score;
                    updateHud();
                    break;
                }
            }
        }

        // spawn
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnEnemyRow();
            spawnTimer = 3.0; // slower waves
        }

        // enemies move & collisions
        double w = canvas.getWidth(), h = canvas.getHeight();
        for (int i = enemies.size()-1; i >= 0; i--) {
            double[] e = enemies.get(i);
            e[0] += e[2] * dt; e[1] += e[3] * dt;

            if (e[0] < E_R)     { e[0] = E_R;     e[2] = Math.abs(e[2]); }
            if (e[0] > w - E_R) { e[0] = w - E_R; e[2] = -Math.abs(e[2]); }

            if (e[1] > h - 40 || hit(px, py, P_R, e[0], e[1], E_R)) {
                enemies.remove(i);
                lives--;
                updateHud();
                if (lives <= 0) { gameOver(); return; }
            }
        }
    }

    private void render(double dt) {
        double w = canvas.getWidth(), h = canvas.getHeight();

        // Fallback background
        if (!videoOk) drawStarfield(dt, w, h);

        // subtle veil so foreground pops
        g.setFill(Color.color(0,0,0, 0.20));
        g.fillRect(0,0,w,h);

        // Advance glow phase for pulsing (≈1.5 pulses per second)
        glowPhase += dt;
        final double pulse = 0.5 + 0.5 * Math.sin(glowPhase * Math.PI * 3.0); // range 0..1

        // player ship (fallback to circle if image missing)
        if (shipImg != null) {
            g.drawImage(shipImg, px - shipHalfW, py - shipHalfH, shipW, shipH);
        } else {
            g.setFill(Color.LIMEGREEN);
            g.fillOval(px - P_R, py - P_R, P_R*2, P_R*2);
        }

        // bullets
        g.setFill(Color.WHITE);
        for (Bullet b: bullets) g.fillOval(b.x - B_R, b.y - B_R, B_R*2, B_R*2);

        // enemies as rockets (rotate to face downward) with pulsing red glow
        if (enemyImg != null) {
            for (double[] e: enemies) {
                double ex = e[0], ey = e[1];

                // --- Red pulsing halo behind the enemy ---
                // Tweakable: base/amp radius and alpha
                double baseR = Math.max(enemyW, enemyH) * 0.10;   // base halo radius
                double ampR  = Math.max(enemyW, enemyH) * 0.20;   // pulse amplitude
                double haloR = baseR + ampR * pulse;               // current halo radius
                double alpha = 0.12 + 0.18 * pulse;                // transparency 0.22..0.40

                g.setFill(Color.color(1.0, 0.2, 0.2, alpha));      // soft red
                g.fillOval(ex - haloR, ey - haloR, haloR*2, haloR*2);

                // Draw centered, rotated 180° to look like incoming rocket
                g.save();
                g.translate(ex, ey);
                g.rotate(180); // pointing downwards
                g.drawImage(enemyImg, -enemyHalfW, -enemyHalfH, enemyW, enemyH);
                g.restore();
            }
        } else {
            // Fallback: red circles with pulsing halo
            for (double[] e: enemies) {
                double ex = e[0], ey = e[1];
                double baseR = E_R * 2.0, ampR = E_R * 0.8;
                double haloR = baseR + ampR * pulse;
                double alpha = 0.22 + 0.18 * pulse;

                g.setFill(Color.color(1.0, 0.2, 0.2, alpha));
                g.fillOval(ex - haloR, ey - haloR, haloR*2, haloR*2);

                g.setFill(Color.CRIMSON);
                g.fillOval(ex - E_R, ey - E_R, E_R*2, E_R*2);
            }
        }
    }

    // --- Starfield fallback (simple + cheap) ---
    private void initStars() {
        stars.clear();
        int count = (int) Math.max(200, (canvas.getWidth() * canvas.getHeight()) / 7000);
        for (int i = 0; i < count; i++) {
            Star s = new Star();
            s.x = rnd.nextDouble() * Math.max(1, canvas.getWidth());
            s.y = rnd.nextDouble() * Math.max(1, canvas.getHeight());
            s.s = 0.5 + rnd.nextDouble() * 2.0; // size
            stars.add(s);
        }
    }

    private void drawStarfield(double dt, double w, double h) {
        g.setFill(Color.BLACK);
        g.fillRect(0,0,w,h);

        g.setFill(Color.web("#a8c7ff"));
        for (Star s : stars) {
            s.y += starSpeed * dt * (0.6 + 0.4 * s.s); // parallax-ish
            if (s.y > h + 10) {
                s.y = -10;
                s.x = rnd.nextDouble() * w;
            }
            double r = s.s;
            g.fillOval(s.x - r, s.y - r, r*2, r*2);
        }
    }

    private void spawnEnemyRow() {
        double w = canvas.getWidth();
        Random rnd = new Random();
        int n = 5 + rnd.nextInt(3);
        for (int i = 0; i < n; i++) {
            double x = 40 + i * ((w - 80) / Math.max(1,n-1));
            double y = -20 - rnd.nextInt(100);
            double vx = (rnd.nextBoolean() ? 1 : -1) * (40 + rnd.nextInt(40)); // slower horizontal wiggle
            double vy = 25 + rnd.nextInt(30);                                  // downward speed
            enemies.add(new double[]{x, y, vx, vy});
        }
    }

    private static boolean hit(double x1,double y1,double r1,double x2,double y2,double r2){
        double dx=x1-x2, dy=y1-y2, rr=r1+r2; return dx*dx+dy*dy <= rr*rr;
    }

    private void clampPlayer() {
        double w = Math.max(2, canvas.getWidth());
        double h = Math.max(2, canvas.getHeight());
        px = Math.max(shipHalfW, Math.min(w - shipHalfW, px));
        py = Math.max(shipHalfH, Math.min(h - shipHalfH, py));
    }

    private void gameOver() {
        if (loop != null) loop.stop();
        running = false;
        saveBest();
        updateHud();
        g.setFill(Color.WHITE);
        g.fillText("Game Over! Score: " + score,
                Math.max(20, canvas.getWidth()*0.5 - 80),
                Math.max(20, canvas.getHeight()*0.5));
    }

    // ===== HUD =====
    private void updateHud() {
        if (scoreLabel != null) scoreLabel.setText("Score: " + score);
        if (livesLabel != null) livesLabel.setText("Lives: " + lives);
        if (bestLabel  != null) bestLabel.setText("Best: " + best);
    }

    // ===== UI actions =====
    @FXML
    private void onRestart() {
        if (loop != null) loop.stop();
        running = false;
        saveBest();
        startSinglePlayer();
    }

    @FXML
    private void onBackToMenu() {
        if (loop != null) loop.stop();
        running = false;
        saveBest();
        stopBackgroundVideo(); // important so media player is disposed
        MainApp.setRoot("/org/example/invaders/rocketblasters/Menu.fxml");
    }

    // ===== Best score local file =====
    private void loadBest() {
        File f = new File("bestscore.txt");
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                best = Integer.parseInt(br.readLine().trim());
            } catch (Exception ignored) {}
        }
    }

    private void saveBest() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("bestscore.txt"))) {
            pw.println(best);
        } catch (Exception ignored) {}
    }
}
