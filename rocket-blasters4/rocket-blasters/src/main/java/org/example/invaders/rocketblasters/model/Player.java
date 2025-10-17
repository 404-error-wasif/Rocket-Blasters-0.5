package org.example.invaders.rocketblasters.model;
import javafx.geometry.Rectangle2D;
public class Player {
    public double x,y,w,h,speed,cooldown=0;
    public Player(double x,double y,double w,double h,double speed){this.x=x;this.y=y;this.w=w;this.h=h;this.speed=speed;}
    public double centerX(){return x+w/2.0;}
    //public Rectangle2D bounds(){return new Rectangle2D(x,y,w,h);}
}
