package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/** A short-lived spark used for muzzle smoke and bullet impacts. */
public class Particle {
    public float x, y, vx, vy, life, maxLife, radius;
    public final Color color;

    public Particle(float x, float y, float angle, float speed, float life, float radius, Color color) {
        this.x = x;
        this.y = y;
        vx = MathUtils.cos(angle) * speed;
        vy = MathUtils.sin(angle) * speed;
        this.life = this.maxLife = life;
        this.radius = radius;
        this.color = new Color(color);
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        float drag = (float) Math.exp(-dt * 3f);
        vx *= drag;
        vy *= drag;
        life -= dt;
    }
}
