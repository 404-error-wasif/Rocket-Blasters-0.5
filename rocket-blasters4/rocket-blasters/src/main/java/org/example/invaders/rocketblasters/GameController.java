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

// At the top of GameController.java
import org.example.invaders.rocketblasters.util.GameMode;
import org.example.invaders.rocketblasters.util.Leaderboard;
import javafx.scene.control.TextInputDialog; // For player name input
import java.util.Optional; // For player name input result

// At the top of GameController.java
import javafx.application.Platform; // Already likely there
import javafx.scene.control.TextInputDialog; // For the pop-up
import java.util.Optional; // To handle the result of the pop-up
import org.example.invaders.rocketblasters.util.GameMode; // To check game mode
import org.example.invaders.rocketblasters.util.Leaderboard; // To save score

public class GameController {

    // ===== FXML =====
    @FXML private Canvas canvas;
    @FXML private Label scoreLabel;
    @FXML private Label livesLabel;
    @FXML private Label bestLabel;
    @FXML private StackPane centerPane;

    // ===== Render/Input =====
    private GraphicsContext g;
    private final Set<KeyCode> keys = new HashSet<>();
    private AnimationTimer loop;
    private boolean running = false;

    // ===== Background video =====
    private MediaPlayer bgPlayer;
    private boolean videoOk = false;
    private boolean needsInitialSpawn = true;// if false, we draw a starfield fallback

    // ===== Player (image ship) =====
    private double px, py, pvx, pvy;
    private static final double P_SPEED = 400;

    private Image shipImg;
    private double shipW = 60, shipH = 80;
    private double shipHalfW = shipW / 2.0, shipHalfH = shipH / 2.0;
    private double P_R = Math.min(shipW, shipH) * 0.38;

    // ===== Bullets =====
    private static final double B_R = 4;
    private static final double B_SPD = 700;
    private static final double FIRE_CD = 0.15;
    private double fireCooldown = 0.0;
    private static class Bullet { double x,y,vy; Bullet(double x,double y,double vy){this.x=x;this.y=y;this.vy=vy;} }
    private final List<Bullet> bullets = new ArrayList<>();

    // ===== Enemies (rocket sprites + pulsing glow) ====
    private final List<double[]> enemies = new ArrayList<>(); // {x,y,vx,vy}
    private double spawnTimer = 0;
    private Image enemyImg;                               // sprite used for enemies

    // draw size for enemy rockets
    private double enemyW = 48, enemyH = 64;
    private double enemyHalfW = enemyW/2.0, enemyHalfH = enemyH/2.0;
    private double E_R = Math.min(enemyW, enemyH) * 0.25;

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

        // Bind canvas size to parent pane size
        if (centerPane != null) {
            canvas.widthProperty().bind(centerPane.widthProperty());
            canvas.heightProperty().bind(centerPane.heightProperty());
        } else {
            System.err.println("Center StackPane not injected - Canvas resizing might not work.");
        }

        loadShipImage();
        loadEnemyImage();// <--- load enemy rocket sprite
        loadPowerUpImages();
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
        bullets.clear();
        enemies.clear();
        powerUps.clear();
        fireCooldown = 0; spawnTimer = 0; lastNs = 0;
        powerUpSpawnTimer = 5.0;

        shieldActive = false; shieldTimer = 0; // Reset shield status
        rapidFireActive = false; rapidFireTimer = 0; // Reset rapid fire status

        pvx = 0; pvy = 0;
        needsInitialSpawn = true;

        if (!videoOk) initStars();
        updateHud();
    }

    private void tick(double dt) {

        if (needsInitialSpawn) {
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            if (w > 0 && h > 0) { // Ensure canvas has valid dimensions
                px = w * 0.5;
                py = h - shipHalfH - 20; // Position near bottom, offset by half height + buffer
                needsInitialSpawn = false; // Only do this once per game start/reset
                System.out.println("Initial spawn: Canvas H=" + h + ", py set to " + py); // Debug
            } else {
                return; // Wait until canvas has dimensions
            }
        }

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
            fireCooldown = rapidFireActive ? (FIRE_CD * RAPID_FIRE_MULTIPLIER) : FIRE_CD;
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

        // --- Power-up Spawning ---
        powerUpSpawnTimer -= dt;
        if (powerUpSpawnTimer <= 0) {
            // Reset timer with some randomness
            powerUpSpawnTimer = 4.0 + rnd.nextDouble() * 4.0; // Spawn between 4-8 seconds

            // Small chance to spawn a power-up
            if (rnd.nextDouble() < 0.3) { // 30% chance each time the timer hits
                spawnRandomPowerUp();
            }
        }

        // --- Power-up Movement & Collection ---
        for (int i = powerUps.size() - 1; i >= 0; i--) {
            PowerUp p = powerUps.get(i);
            p.y += p.vy * dt;

            // Remove if off-screen
            if (p.y > canvas.getHeight() + p.radius * 2) {
                powerUps.remove(i);
                continue;
            }

            // Check collision with player
            if (hit(px, py, P_R, p.x, p.y, p.radius)) {
                applyPowerUp(p.type);
                powerUps.remove(i);
                // Add sound effect here if you have one
            }
        }

        // --- Update Active Power-up Timers ---
        if (shieldActive) {
            shieldTimer -= dt;
            if (shieldTimer <= 0) {
                shieldActive = false;
            }
        }
        if (rapidFireActive) {
            rapidFireTimer -= dt;
            if (rapidFireTimer <= 0) {
                rapidFireActive = false;
            }
        }

        // enemies move & collisions
        double w = canvas.getWidth(), h = canvas.getHeight();
        for (int i = enemies.size()-1; i >= 0; i--) {
            double[] e = enemies.get(i);
            e[0] += e[2] * dt; e[1] += e[3] * dt;

            if (e[0] < E_R)     { e[0] = E_R;     e[2] = Math.abs(e[2]); }
            if (e[0] > w - E_R) { e[0] = w - E_R; e[2] = -Math.abs(e[2]); }

            //previous collision check:
            /*
            if (e[1] > h - 40 || hit(px, py, P_R, e[0], e[1], E_R)) {
                enemies.remove(i);
                lives--; // <-- THIS LINE
                updateHud();
                if (lives <= 0) { gameOver(); return; }
            }
            */
            // With this check for the shield:

            if (e[1] > h - 40 || hit(px, py, P_R, e[0], e[1], E_R)) {
                enemies.remove(i);
                if (!shieldActive) { // Only lose a life if shield is NOT active
                    lives--;
                    updateHud();
                    if (lives <= 0) { gameOver(); return; }
                } else {
                    // Shield absorbed the hit - optionally deactivate shield immediately or play a sound
                    shieldActive = false; // Example: Shield breaks on hit
                    shieldTimer = 0;
                    // Maybe play shield impact sound
                }
            }
        }
    }

    private void render(double dt) {
        clampPlayer();
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
            double currentCanvasHeight = canvas.getHeight(); // Get height right before drawing
            // Calculate the maximum allowed Y for the ship's center to keep it fully visible
            double maxY = currentCanvasHeight - shipHalfH;
            // Ensure the drawing Y position doesn't exceed this limit
            double drawY = Math.min(py, maxY); // Use the clamped py OR maxY, whichever is smaller
            // Also ensure it doesn't go above the top
            drawY = Math.max(shipHalfH, drawY);

            // *** Use drawY for drawing, instead of py directly ***
            g.drawImage(shipImg, px - shipHalfW, drawY - shipHalfH, shipW, shipH);

            // Optional Debug Print (uncomment to check values):
            // if (py > maxY) {
            //     System.out.printf("RENDER CLAMP: py=%.1f > maxY=%.1f. Drawing at %.1f (CanvasH=%.1f)\n", py, maxY, drawY, currentCanvasHeight);
            } else {
            // Fallback drawing (circle) - Apply similar clamping if needed
            double currentCanvasHeight = canvas.getHeight();
            double maxY = currentCanvasHeight - P_R;
            double drawY = Math.min(py, maxY);
            drawY = Math.max(P_R, drawY); // Use P_R for radius clamping here

            g.setFill(Color.LIMEGREEN);
            g.fillOval(px - P_R, drawY - P_R, P_R*2, P_R*2);
        }

        // bullets
        g.setFill(Color.WHITE);
        for (Bullet b: bullets) g.fillOval(b.x - B_R, b.y - B_R, B_R*2, B_R*2);

        // --- Render Power-ups ---
        for (PowerUp p : powerUps) {
            Image imgToDraw = null;
            switch (p.type) {
                case SHIELD:       imgToDraw = shieldPowerUpImg;       break;
                case RAPID_FIRE:   imgToDraw = rapidFirePowerUpImg;   break;
                case EXTRA_LIFE:   imgToDraw = extraLifePowerUpImg;   break;
                case SCORE_BONUS:  imgToDraw = scoreBonusPowerUpImg;  break;
            }

            if (imgToDraw != null) {
                // Draw the image centered at the power-up's position
                g.drawImage(imgToDraw, p.x - powerUpHalfW, p.y - powerUpHalfH, powerUpDrawW, powerUpDrawH);
            } else {
                // Fallback: Draw colored circles if image loading failed
                switch (p.type) {
                    case SHIELD:       g.setFill(Color.CYAN);       break;
                    case RAPID_FIRE:   g.setFill(Color.ORANGERED); break;
                    case EXTRA_LIFE:   g.setFill(Color.LIMEGREEN); break;
                    case SCORE_BONUS:  g.setFill(Color.GOLD);       break;
                    default:           g.setFill(Color.MAGENTA);   break;
                }
                g.fillOval(p.x - p.radius, p.y - p.radius, p.radius * 2, p.radius * 2);
            }
        }

        // --- Render Shield Effect ---
        if (shieldActive) {
            double shieldRadius = P_R * 1.5; // Make shield visual slightly larger than collision radius
            // Draw a semi-transparent blue circle around the player
            g.setFill(Color.color(0.3, 0.5, 1.0, 0.4)); // Light blue, semi-transparent
            g.fillOval(px - shieldRadius, py - shieldRadius, shieldRadius * 2, shieldRadius * 2);
            // Optionally add a border
            g.setStroke(Color.color(0.7, 0.8, 1.0, 0.7));
            g.setLineWidth(2);
            g.strokeOval(px - shieldRadius, py - shieldRadius, shieldRadius * 2, shieldRadius * 2);
            g.setLineWidth(1); // Reset line width
        }

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

    // Inside GameController.javas
    private void clampPlayer() {
        double w = canvas.getWidth();  // Current canvas width
        double h = canvas.getHeight(); // Current canvas height

        // Check if dimensions are valid (avoid dividing by zero or clamping in tiny space)
        if (w <= shipW || h <= shipH) {
            // Optional: Print a warning if canvas size is problematic
            // System.out.println("WARN: Canvas size too small for clamping (" + w + "x" + h + ")");
            return;
        }

        // --- Clamping Logic ---
        // Clamp Left boundary
        px = Math.max(shipHalfW, Math.min(w - shipHalfW, px));
        // Clamp Right boundary
        py = Math.max(shipHalfH, Math.min(h - shipHalfH, py));

        double bottomLimit = h - shipHalfH;
        if (py > bottomLimit) {
            System.out.println("CORRECTING: py (" + py + ") exceeded bottomLimit (" + bottomLimit + ")"); // Debug
            py = bottomLimit;
        }

        // Clamp Top boundary
        //py = Math.max(shipHalfH, py);
        // Clamp Bottom boundary
        //py = Math.min(h - shipHalfH, py); // Prevent ship center going below h - half height

        // --- Debugging ---
        // Uncomment the line below temporarily if needed to see values:
        // System.out.printf("Canvas H: %.1f, Ship py: %.1f, Clamped py: %.1f, Max py allowed: %.1f\n", h, pyBeforeClamp, py, h - shipHalfH);
        // Note: To use pyBeforeClamp, you'd need to store the py value before clamping.
        // Simpler check: Print py after clamping
        // System.out.printf("Clamped py: %.1f (Max allowed: %.1f)\n", py, h - shipHalfH);
    }

    private void gameOver() {
        System.out.println("DEBUG: gameOver() method called. Score: " + score + ", Lives: " + lives);
        if (loop != null) loop.stop();
        running = false;
        handleEndOfGameScore();
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

    // ===== Power-ups =====

    // Images for Power-ups
    private Image shieldPowerUpImg;
    private Image rapidFirePowerUpImg;
    private Image extraLifePowerUpImg;
    private Image scoreBonusPowerUpImg;

    // Define image paths (adjust these if your filenames are different)
    private static final String SHIELD_IMG_PATH = "/org/example/invaders/rocketblasters/assets/shield_powerup.png"; // Example path
    private static final String RAPID_FIRE_IMG_PATH = "/org/example/invaders/rocketblasters/assets/rapidfire_powerup.png"; // Example path
    private static final String EXTRA_LIFE_IMG_PATH = "/org/example/invaders/rocketblasters/assets/extralife_powerup.png"; // Example path
    private static final String SCORE_BONUS_IMG_PATH = "/org/example/invaders/rocketblasters/assets/scorebonus_powerup.png"; // Example path

    // Define draw size for power-up images (adjust as needed)
    private double powerUpDrawW = 30;
    private double powerUpDrawH = 30;
    private double powerUpHalfW = powerUpDrawW / 2.0;
    private double powerUpHalfH = powerUpDrawH / 2.0;

    // ... (rest of the power-up related fields like powerUps list, timers, etc.) ...

    private enum PowerUpType {
        SHIELD, RAPID_FIRE, EXTRA_LIFE, SCORE_BONUS
    }

    private static class PowerUp {
        double x, y, vy;
        PowerUpType type;
        double radius = 15; // Collision and drawing radius

        PowerUp(double x, double y, double vy, PowerUpType type) {
            this.x = x;
            this.y = y;
            this.vy = vy;
            this.type = type;
        }
    }

    private final List<PowerUp> powerUps = new ArrayList<>();
    private double powerUpSpawnTimer = 5.0; // Spawn roughly every 5 seconds initially

    // Timers for temporary effects
    private boolean shieldActive = false;
    private double shieldTimer = 0.0;
    private static final double SHIELD_DURATION = 5.0; // seconds

    private boolean rapidFireActive = false;
    private double rapidFireTimer = 0.0;
    private static final double RAPID_FIRE_DURATION = 7.0; // seconds
    private static final double RAPID_FIRE_MULTIPLIER = 0.5; // Halves the cooldown

    // Inside GameController.java

    private void spawnRandomPowerUp() {
        double w = canvas.getWidth();
        double spawnX = 50 + rnd.nextDouble() * (w - 100); // Spawn within screen bounds
        double spawnY = -30; // Start just above the screen
        double fallSpeed = 100 + rnd.nextDouble() * 50; // Random fall speed

        // Choose a random type
        PowerUpType[] types = PowerUpType.values();
        PowerUpType randomType = types[rnd.nextInt(types.length)];

        powerUps.add(new PowerUp(spawnX, spawnY, fallSpeed, randomType));
    }

    // Inside GameController.java

    private void applyPowerUp(PowerUpType type) {
        switch (type) {
            case SHIELD:
                shieldActive = true;
                shieldTimer = SHIELD_DURATION;
                // Maybe play shield activation sound
                break;
            case RAPID_FIRE:
                rapidFireActive = true;
                rapidFireTimer = RAPID_FIRE_DURATION;
                // Maybe play rapid fire sound cue
                break;
            case EXTRA_LIFE:
                if (lives < 5) { // Cap lives at 5, for example
                    lives++;
                    updateHud();
                    // Maybe play extra life sound
                }
                break;
            case SCORE_BONUS:
                score += 100; // Add 100 points
                if (score > best) best = score; // Check if it pushes score over best
                updateHud();
                // Maybe play score bonus sound
                break;
        }
    }

    // load power-up images

    private void loadPowerUpImages() {
        try {
            shieldPowerUpImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(SHIELD_IMG_PATH)));
        } catch (Exception ex) {
            System.err.println("Could not load Shield power-up image: " + SHIELD_IMG_PATH);
            shieldPowerUpImg = null; // Fallback handled in render
        }
        try {
            rapidFirePowerUpImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(RAPID_FIRE_IMG_PATH)));
        } catch (Exception ex) {
            System.err.println("Could not load Rapid Fire power-up image: " + RAPID_FIRE_IMG_PATH);
            rapidFirePowerUpImg = null;
        }
        try {
            extraLifePowerUpImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(EXTRA_LIFE_IMG_PATH)));
        } catch (Exception ex) {
            System.err.println("Could not load Extra Life power-up image: " + EXTRA_LIFE_IMG_PATH);
            extraLifePowerUpImg = null;
        }
        try {
            scoreBonusPowerUpImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(SCORE_BONUS_IMG_PATH)));
        } catch (Exception ex) {
            System.err.println("Could not load Score Bonus power-up image: " + SCORE_BONUS_IMG_PATH);
            scoreBonusPowerUpImg = null;
        }
    }

    // Inside GameController.java

    private void handleEndOfGameScore() {
        System.out.println("DEBUG: handleEndOfGameScore() called. Current Mode: " + MainApp.currentGameMode); // Keep for debugging
        saveBest(); // Always try saving overall best score

        // REMOVED THE IF CHECK - Pop-up logic will now always run:
        System.out.println("DEBUG: Preparing pop-up for leaderboard entry."); // Updated debug message

        Platform.runLater(() -> {
            System.out.println("DEBUG: Inside Platform.runLater - Creating TextInputDialog.");
            TextInputDialog dialog = new TextInputDialog("Player"); // Default text
            dialog.setTitle("Game Over - Leaderboard Entry");
            dialog.setHeaderText("Congratulations! Your score: " + score);
            dialog.setContentText("Please enter your name for the leaderboard:");

            Optional<String> result = dialog.showAndWait();
            System.out.println("DEBUG: Dialog shown. Result present: " + result.isPresent());

            result.ifPresent(name -> {
                String playerName = name.isBlank() ? "Anonymous" : name.trim();
                System.out.println("DEBUG: Recording score for: " + playerName);
                Leaderboard.record(playerName, score); // Record score regardless of mode
            });
        });
    }

}
