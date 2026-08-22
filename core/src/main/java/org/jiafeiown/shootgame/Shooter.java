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
    public static final float UP_BOOST = 300f;
    public static final float UP_MIN = 200f;
    public static final float MUZZLE_OFFSET = 102f;
    public static final float BULLET_SPEED = 980f;
    public static final float KNOCKBACK = 130f;
    public static final int BULLET_DAMAGE = 14;
    public static final int BASE_HP = 100;
    /** Seconds a freshly spawned enemy spends materializing: it can neither
     *  move nor fire, takes no damage, and fades in from transparent. */
    public static final float SPAWN_TIME = 1f;
    /** Seconds a defeated enemy lingers on screen while its dissolve-out
     *  plays: the body fades away, tips over and sinks slightly. */
    public static final float DEATH_TIME = 0.5f;

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
    public int damage;
    public float dodgeCooldown;
    public int burst;
    /** Remaining seconds of the spawn fade-in; 0 once fully materialized.
     *  Only armed for enemies ({@link RoundManager} sets it on spawn); the
     *  player spawns instantly. While > 0 the shooter is frozen in place:
     *  no physics, no AI actions and no damage taken. */
    public float spawnTimer = 0f;
    /** Remaining seconds of the death dissolve-out; armed only for enemies
     *  at the moment they die ({@code dead == true}). While > 0 the corpse is
     *  still drawn, fading away; round progression waits for it to finish. */
    public float deathTimer = 0f;
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

    /** True while this shooter is still materializing after its spawn. */
    public boolean isSpawning() {
        return spawnTimer > 0f;
    }

    /** Fade-in progress of the spawn transition, 0 (invisible) → 1 (solid),
     *  shaped with a smoothstep so the appearance eases in rather than
     *  ramping linearly. */
    public float spawnAlpha() {
        float a = MathUtils.clamp(1f - spawnTimer / SPAWN_TIME, 0f, 1f);
        return a * a * (3f - 2f * a);
    }

    /** True while this dead shooter's dissolve-out is still on screen. */
    public boolean isDying() {
        return dead && deathTimer > 0f;
    }

    /** Fade-out progress of the death transition, 1 (just died) → 0 (gone). */
    public float deathAlpha() {
        return MathUtils.clamp(deathTimer / DEATH_TIME, 0f, 1f);
    }

    public void update(float dt) {
        // dead shooters keep only their dissolve timer ticking; everything
        // else about them is frozen exactly as they fell
        if (dead) {
            if (deathTimer > 0f) deathTimer = Math.max(0f, deathTimer - dt);
            return;
        }
        // still materializing: hold perfectly still until the fade-in ends
        if (spawnTimer > 0f) {
            spawnTimer = Math.max(0f, spawnTimer - dt);
            return;
        }
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

        // the muzzle spins while airborne; resting on the ground halts the rotation
        if (!grounded) {
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

    /**
     * True if the segment travelled by a bullet this frame (spawn point →
     * current position) touches any part of the gun. Swept, so it does not
     * depend on how far a single frame's movement is (i.e. on frame rate):
     * a fast bullet can no longer tunnel through the gun's thin parts, and a
     * bullet that spawns on top of a shooter registers immediately instead of
     * only when the frame rate happens to leave it inside the hitbox.
     */
    public boolean bulletSegmentHits(float bx0, float by0, float bx1, float by1, float radius) {
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);
        float dx0 = bx0 - x, dy0 = by0 - y;
        float lx0 = cos * dx0 + sin * dy0;
        float ly0 = -sin * dx0 + cos * dy0;
        float dx1 = bx1 - x, dy1 = by1 - y;
        float lx1 = cos * dx1 + sin * dy1;
        float ly1 = -sin * dx1 + cos * dy1;
        for (float[] hb : HITBOXES) {
            if (Geometry.segmentHitsBox(lx0, ly0, lx1, ly1,
                    hb[0] - radius, hb[1] - radius, hb[2] + radius, hb[3] + radius)) {
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
        // bullets pass through a shooter that hasn't finished materializing
        if (isSpawning()) return;
        if (invincibleTime > 0f) return;
        hp -= dmg;
        if (hp < 0) hp = 0;
        vx += nx * KNOCKBACK;
        vy += ny * KNOCKBACK;
        if (hp == 0) {
            dead = true;
            // enemies linger briefly and dissolve instead of vanishing in a
            // single frame; the player just drops (the game-over screen takes over)
            if (!isPlayer) deathTimer = DEATH_TIME;
            log.debug("{} died ({} damage taken)", isPlayer ? "Player" : "Enemy", dmg);
        } else {
            log.trace("{} took {} damage (hp {} → {})", isPlayer ? "Player" : "Enemy", dmg, hp + dmg, hp);
        }
    }
}
