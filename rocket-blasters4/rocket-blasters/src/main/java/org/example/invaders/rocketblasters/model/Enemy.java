package org.example.invaders.rocketblasters.model;
import javafx.geometry.Rectangle2D;
public class Enemy {
    public double x,y,w,h;
    public Enemy(double x,double y,double w,double h){this.x=x;this.y=y;this.w=w;this.h=h;}
    public double centerX(){return x+w/2.0;}
    //public Rectangle2D bounds(){return new Rectangle2D(x,y,w,h);}
}
