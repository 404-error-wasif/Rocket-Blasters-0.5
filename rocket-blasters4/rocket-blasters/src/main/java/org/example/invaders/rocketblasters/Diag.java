package org.example.invaders.rocketblasters;

public class Diag {
    public static void main(String[] args) {
        checkClass("org.example.invaders.rocketblasters.MainApp");
        checkClass("org.example.invaders.rocketblasters.MenuController");
        checkClass("org.example.invaders.rocketblasters.GameController");
        checkClass("org.example.invaders.rocketblasters.HeadToHeadController");
        checkClass("org.example.invaders.rocketblasters.H2HGameController");
        checkClass("org.example.invaders.rocketblasters.net.NetLink");
        checkClass("org.example.invaders.rocketblasters.net.NetServer");
        checkClass("org.example.invaders.rocketblasters.net.NetClient");
        checkRes("/org/example/invaders/rocketblasters/Menu.fxml");
        checkRes("/org/example/invaders/rocketblasters/BattleView.fxml");
        checkRes("/org/example/invaders/rocketblasters/H2HGameView.fxml");
        System.out.println("Diag OK if none say MISSING.");
    }

    private static void checkClass(String name) {
        try {
            Class.forName(name);
            System.out.println("OK  class " + name);
        } catch (Throwable t) {
            System.out.println("MISSING class " + name + "  -> " + t);
        }
    }
    private static void checkRes(String path) {
        try {
            var url = Diag.class.getResource(path);
            System.out.println((url != null ? "OK  " : "MISSING ") + "resource " + path);
        } catch (Throwable t) {
            System.out.println("MISSING resource " + path + "  -> " + t);
        }
    }
}
