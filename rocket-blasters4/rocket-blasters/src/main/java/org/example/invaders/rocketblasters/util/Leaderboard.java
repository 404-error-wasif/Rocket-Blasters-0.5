package org.example.invaders.rocketblasters.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Leaderboard {
    private static final Path LB = Paths.get(System.getProperty("user.home"), "rb_leaderboard.csv");

    public static synchronized void record(String name, int score) {
        try {
            List<String> lines = Files.exists(LB) ? Files.readAllLines(LB, StandardCharsets.UTF_8) : new ArrayList<>();
            lines.add(name.replace(",", " ") + "," + score + "," + System.currentTimeMillis());
            Files.write(LB, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static synchronized List<String> topN(int n) {
        try {
            if (!Files.exists(LB)) return List.of();
            List<String> lines = Files.readAllLines(LB, StandardCharsets.UTF_8);
            lines.sort((a,b) -> {
                int sa = Integer.parseInt(a.split(",")[1]);
                int sb = Integer.parseInt(b.split(",")[1]);
                return Integer.compare(sb, sa);
            });
            return lines.subList(0, Math.min(n, lines.size()));
        } catch (Exception e) { return List.of(); }
    }
}
