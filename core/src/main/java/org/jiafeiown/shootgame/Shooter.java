package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A pistol fighter. Both the player and the enemy are Shooters. */
public class Shooter {
    private static final Logger log = LogManager.getLogger(Shooter.class);
    public static final float GRAVITY = 900f;
    public static final float RECOIL = 230f;
    public static final float UP_BOOST = 400f;
    public static final float UP_MIN = 200f;
    public static final float MUZZLE_OFFSET = 102f;
    public static final float BULLET_SPEED = 980f;
    public static final float KNOCKBACK = 130f;
    public static final int BULLET_DAMAGE = 14;
    public static final int BASE_HP = 100;
    public static final float SPIN_STUN = 0.9f;

    public final boolean isPlayer;
    public float x, y, vx, vy, angle;
    public int hp;
    public final int maxHp;
    public float fireCooldown;
    public final float fireInterval;
    public boolean dead;
    public boolean grounded;
    public float recoilVis = 0f;
    public float spin = 2.2f;
    public float spinStun = 0f;
    public int damage;
    public float dodgeCooldown;
    public int burst;
    /** Remaining seconds of damage immunity; decremented in {@link #update}.
     *  Only the player uses it today, but it is generic on purpose. */
    public float invincibleTime;

    public final float halfW = 51f;
    public final float halfH = 21f;

    public final Color body, barrel, grip, slide, bullet, bulletCore;

    public Shooter(boolean isPlayer, float x, float y, int maxHp, float fireInterval,
                   Color body, Color barrel, Color grip, Color slide, Color bullet, Color bulletCore) {
        this.isPlayer = isPlayer;
        this.x = x;
        this.y = y;
        this.hp = this.maxHp = maxHp;
        this.fireInterval = fireInterval;
        this.damage = BULLET_DAMAGE;
        this.body = body;
        this.barrel = barrel;
        this.grip = grip;
        this.slide = slide;
        this.bullet = bullet;
        this.bulletCore = bulletCore;
        log.debug("{} spawned at ({}, {}) hp={} fireInterval={}",
                isPlayer ? "Player" : "Enemy", x, y, maxHp, fireInterval);
    }

    public void update(float dt) {
        if (dead) return;
        if (invincibleTime > 0f) invincibleTime = Math.max(0f, invincibleTime - dt);
        fireCooldown -= dt;
        recoilVis *= (float) Math.exp(-dt * 14f);

        vy -= GRAVITY * dt;
        if (vy < -1600f) vy = -1600f;
        x += vx * dt;
        y += vy * dt;

        float ry = halfHeight();
        if (y - ry < WorldConfig.GROUND_TOP) {
            y = WorldConfig.GROUND_TOP + ry;
            if (vy < 0f) vy = 0f;
            grounded = true;
        } else {
            grounded = false;
        }
        if (y + ry > WorldConfig.WORLD_H) {
            y = WorldConfig.WORLD_H - ry;
            if (vy > 0f) vy = 0f;
        }

        float rx = halfWidth();
        if (x - rx < 0f) {
            x = rx;
            if (vx < 0f) vx = 0f;
        }
        if (x + rx > WorldConfig.WORLD_W) {
            x = WorldConfig.WORLD_W - rx;
            if (vx > 0f) vx = 0f;
        }

        if (grounded) {
            vx *= (1f - Math.min(1f, 2.6f * dt));
        } else {
            vx *= (1f - Math.min(1f, 0.2f * dt));
        }

        // the muzzle spins while airborne; resting on the ground (or a
        // recent bullet hit) halts the rotation
        if (spinStun > 0f) {
            spinStun -= dt;
        } else if (!grounded) {
            angle += spin * dt;
            if (angle > MathUtils.PI2) angle -= MathUtils.PI2;
            else if (angle < -MathUtils.PI2) angle += MathUtils.PI2;
        }
    }

    public float halfWidth() {
        float c = Math.abs(MathUtils.cos(angle));
        float s = Math.abs(MathUtils.sin(angle));
        return halfW * c + halfH * s;
    }

    public float halfHeight() {
        float c = Math.abs(MathUtils.cos(angle));
        float s = Math.abs(MathUtils.sin(angle));
        return halfW * s + halfH * c;
    }

    /** Local collision boxes (in 1.5x-scaled sprite units) outlining the pistol. */
    static final float[][] HITBOXES = {
        {-6f, -18f, 69f, 18f},     // body
        {60f, -7.5f, 102f, 7.5f},  // barrel
        {96f, -6f, 105f, 6f},      // front grip
        {3f, -45f, 30f, -15f},     // back grip / handle
    };

    /** True if a bullet at (bx, by) of the given radius touches any part of the gun. */
    public boolean bulletHits(float bx, float by, float radius) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float dx = bx - x;
        float dy = by - y;
        float lx = cos * dx + sin * dy;
        float ly = -sin * dx + cos * dy;
        for (float[] hb : HITBOXES) {
            if (lx >= hb[0] - radius && lx <= hb[2] + radius
                    && ly >= hb[1] - radius && ly <= hb[3] + radius) {
                return true;
            }
        }
        return false;
    }

    public void shoot(GameWorld world) {
        log.trace("{} fires at angle {} rad from ({}, {})", isPlayer ? "Player" : "Enemy", angle, x, y);
        fireCooldown = fireInterval;
        float dx = MathUtils.cos(angle);
        float dy = MathUtils.sin(angle);
        float mx = x + dx * MUZZLE_OFFSET;
        float my = y + dy * MUZZLE_OFFSET;
        // guarantee a rise on every shot, even when falling hard:
        // first pull the fall up to a safe floor, then add the upward boost
        if (vy < UP_MIN) vy = UP_MIN;
        vy += UP_BOOST;
        // then the muzzle recoil, opposite the aim direction
        vx -= dx * RECOIL;
        vy -= dy * RECOIL;
        recoilVis = 1f;
        world.spawnBullet(this, mx, my, angle);
    }

    public void takeDamage(int dmg, float nx, float ny) {
        if (dead) return;
        if (invincibleTime > 0f) return;
        hp -= dmg;
        if (hp < 0) hp = 0;
        vx += nx * KNOCKBACK;
        vy += ny * KNOCKBACK;
        // being hit interrupts the spinning muzzle
        spinStun = SPIN_STUN;
        if (hp == 0) {
            dead = true;
            log.debug("{} died ({} damage taken)", isPlayer ? "Player" : "Enemy", dmg);
        } else {
            log.trace("{} took {} damage (hp {} → {})", isPlayer ? "Player" : "Enemy", dmg, hp + dmg, hp);
        }
    }
}
