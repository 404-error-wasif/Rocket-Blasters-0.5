package org.example.invaders.rocketblasters.util;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public class HighScore {
    private static final Path SCORE_FILE = Paths.get(System.getProperty("user.home"), "score.txt");
    public static int loadBest(){
        try{ if(Files.exists(SCORE_FILE)) return Integer.parseInt(Files.readString(SCORE_FILE, StandardCharsets.UTF_8).trim()); }
        catch(Exception ignored){}
        return 0;
    }
    public static int saveIfBest(int score){
        int current = loadBest();
        if(score>current){
            try{ Files.writeString(SCORE_FILE, Integer.toString(score), StandardCharsets.UTF_8); }catch(Exception ignored){}
            return score;
        }
        return current;
    }
}
