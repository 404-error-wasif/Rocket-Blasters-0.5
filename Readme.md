# Rocket Blasters 🚀

A classic arcade space shooter built with JavaFX. This project features a dynamic single-player mode, a persistent leaderboard, and an experimental real-time head-to-head (H2H) multiplayer mode.

## ✨ Features

  * **Video Splash Screen:** Launches with a full-screen introductory video before loading the menu.
  * **Dynamic Full-Screen Display:** The application runs in full-screen mode, with the game canvas dynamically resizing to fit the available space.
  * **Styled UI:** The main menu features a custom space background image and modern, styled buttons.
  * **Multiple Game Modes**:
      * **Single Player:** Classic arcade mode.
      * **Multiplayer Ranked:** Play single-player and submit your score to the leaderboard.
      * **Head-to-Head (Online):** Experimental 1v1 multiplayer over a network connection.
  * **Power-Up System:** Collect randomly spawning power-ups in single-player mode:
      * **Shield:** Absorbs one enemy hit.
      * **Rapid Fire:** Temporarily increases shooting speed.
      * **Extra Life:** Grants an additional life.
      * **Score Bonus:** Instantly awards 100 points.
      * **Spread Shot:** Fires 3 bullets in a 45-degree spread for 15s.
      * **Triple Shot:** Fires 3 bullets in parallel lines for 15s.
      * **Invincibility:** Makes the ship immune to damage for 15s.
      * **Last Stand:** Spawns only when lives are at 1 and absorbs the next fatal hit.
  * **Persistent Scoring:**
      * Saves the all-time best score locally (`bestscore.txt`).
      * Saves ranked scores to a local CSV file (`rb_leaderboard.csv`) after prompting for a name.
  * **Leaderboard Screen:** Displays the top 20 scores from the ranked mode file.
  * **Sound Effects:** Features sound for shooting and taking damage.

## 🕹️ Gameplay

### Controls

  * **Move:** `A`/`D` or `Left Arrow`/`Right Arrow`
  * **Move Up/Down:** `W`/`S` or `Up Arrow`/`Down Arrow` (in single-player)
  * **Shoot:** `Space` or `Enter`
  * **Restart:** `Enter` (from game over or during play)
  * **Back to Menu:** `Esc`

### Objective

**Single Player / Ranked:** Shoot as many enemies as possible to get a high score. Avoid colliding with enemies, which costs a life. Collect power-ups to gain temporary advantages. The game ends when you run out of lives.

**Head-to-Head:** Compete against another player. Damage your opponent by shooting them. The player with more HP remaining when the timer runs out (or the last one standing) wins.

## 🛠️ How to Build and Run

This is a Maven project built with JavaFX.

### Prerequisites

  * **Java JDK 21** (or higher)
  * **JavaFX SDK 17+** (The project `pom.xml` references 21.0.8, but your local setup uses 17.0.16)
  * **Apache Maven**

### Recommended: Add VM Options to POM

To avoid runtime errors with JavaFX modules, it's best to add the required VM options directly to the `pom.xml`.

1.  Open `pom.xml`.
2.  Find the `<plugin>` for `javafx-maven-plugin`.
3.  Add an `<options>` block inside the `<configuration>` section:

<!-- end list -->

```xml
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <mainClass>org.example.invaders.rocketblasters.MainApp</mainClass>
        <launcher>launcher</launcher>
        <options>
            <option>--add-modules</option>
            <option>javafx.controls,javafx.fxml,javafx.media</option>
            <option>--add-opens</option>
            <option>javafx.graphics/com.sun.javafx.sg.prism=javafx.media</option>
        </options>
        </configuration>
</plugin>
```

### Running from Command Line

1.  Open a terminal in the project's root directory (where `pom.xml` is located).
2.  Clean and build the project:
    ```sh
    mvn clean package
    ```
3.  Run the application using the JavaFX plugin:
    ```sh
    mvn javafx:run
    ```

### Running from IntelliJ IDEA

1.  Open the project in IntelliJ (open the `pom.xml` file as a project).
2.  Let IntelliJ import the Maven dependencies.
3.  If you did *not* add the VM options to the `pom.xml` (step above), you must add them to your Run Configuration:
      * Go to `Run` -\> `Edit Configurations...`.
      * Find your `MainApp` configuration (or create one).
      * In the **VM options** field, add:
        ```
        --add-modules javafx.controls,javafx.fxml,javafx.media --add-opens javafx.graphics/com.sun.javafx.sg.prism=javafx.media
        ```
      * Make sure your **Module Path** is correctly set up to point to your JavaFX SDK `lib` folder (IntelliJ often does this via Maven).
4.  Run the `MainApp` class.

## 💻 Tech Stack

  * **Java**
  * **JavaFX** (for graphics, UI, and media)
  * **FXML** (for UI layout)
  * **Maven** (for build and dependency management)
  * **Java Sockets** (for H2H networking)

## 🔮 Future Ideas

  * **Boss Battles:** Add large, unique enemies at the end of waves.
  * **Database Leaderboard:** Replace the local CSV leaderboard with a persistent database (MySQL connector is already a dependency).
  * **More Enemies:** Create enemies with different movement patterns and firing abilities.
  * **Refined H2H Mode:** Improve network synchronization and add win/lose sound effects.