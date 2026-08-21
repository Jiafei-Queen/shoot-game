package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;

/** A brief burst of flame at the muzzle when a shot is fired. */
public class MuzzleFlash {
    public float x, y, angle, life, maxLife;
    public final Color tint;

    public MuzzleFlash(float x, float y, float angle, Color tint) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.tint = new Color(tint);
        life = maxLife = 0.09f;
    }
}
