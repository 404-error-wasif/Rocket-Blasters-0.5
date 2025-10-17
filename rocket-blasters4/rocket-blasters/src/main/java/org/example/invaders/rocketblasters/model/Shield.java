package org.example.invaders.rocketblasters.model;
import javafx.geometry.Rectangle2D;
public class Shield {
    public double x,y,w,h; private int hp; private final int maxHp;
    public Shield(double x,double y,double w,double h,int hp){this.x=x;this.y=y;this.w=w;this.h=h;this.hp=hp;this.maxHp=hp;}
    public void hit(){ if(hp>0) hp--; }
    public boolean isBroken(){ return hp<=0; }
    public double integrityRatio(){ return Math.max(0,(double)hp/(double)maxHp); }
    public Rectangle2D bounds(){ return new Rectangle2D(x,y,w,h); }
}
