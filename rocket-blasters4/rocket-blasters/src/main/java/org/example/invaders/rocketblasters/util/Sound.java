package org.example.invaders.rocketblasters.util;
import javafx.scene.media.AudioClip;
import java.util.*;
public class Sound {
    private static final Map<String, AudioClip> bank = new HashMap<>();
    public static void preload(String... paths){
        for(String p: paths){
            try{
                AudioClip ac = new AudioClip(Objects.requireNonNull(Sound.class.getResource(p)).toExternalForm());
                bank.put(p, ac);
            }catch(Exception ignored){}
        }
    }
    public static void play(String path,double volume){
        AudioClip ac = bank.get(path);
        if(ac!=null){ ac.setVolume(volume); ac.play(); }
    }
}
