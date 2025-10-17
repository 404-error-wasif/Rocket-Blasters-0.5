package org.example.invaders.rocketblasters.model;
import javafx.geometry.Rectangle2D;
public class Bullet {
    public double x,y,w,h,vy;
    public Bullet(double x,double y,double w,double h,double vy){this.x=x;this.y=y;this.w=w;this.h=h;this.vy=vy;}
    public Rectangle2D bounds(){return new Rectangle2D(x,y,w,h);}
}
