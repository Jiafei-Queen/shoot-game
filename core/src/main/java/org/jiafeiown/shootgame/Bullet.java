package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/** A bullet fired by a Shooter. Hurts anyone it touches, friend or foe. */
public class Bullet {
    public float x, y, prevX, prevY, vx, vy, life;
    public final float nx, ny;
    public final Color color, core;
    public final Shooter owner;
    public boolean dead;

    public Bullet(Shooter owner, float x, float y, float angle) {
        this.owner = owner;
        this.x = x;
        this.y = y;
        prevX = x;
        prevY = y;
        nx = MathUtils.cos(angle);
        ny = MathUtils.sin(angle);
        vx = nx * Shooter.BULLET_SPEED;
        vy = ny * Shooter.BULLET_SPEED;
        color = new Color(owner.bullet);
        core = new Color(owner.bulletCore);
        life = 1.6f;
    }

    public void update(float dt) {
        prevX = x;
        prevY = y;
        x += vx * dt;
        y += vy * dt;
        life -= dt;
        if (life <= 0f) dead = true;
        if (y < GameWorld.GROUND_TOP + 2f || y > GameWorld.WORLD_H
                || x < -24f || x > GameWorld.WORLD_W + 24f) {
            dead = true;
        }
    }
}
