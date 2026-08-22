package org.jiafeiown.shootgame;

import com.badlogic.gdx.math.MathUtils;

/**
 * Enemy shooting/aiming AI. Decides when each enemy fires, leads the moving
 * target, and dodges incoming player bullets with a counter-shot whose recoil
 * shoves the enemy off the bullet's path.
 *
 * <p>Reads world state (player, bullets, enemies) through its {@link GameWorld}
 * reference and triggers shots via {@link GameWorld#fire(Shooter)}; it never
 * mutates world-level structures itself.
 */
public class EnemyAI {

    private static final float ENEMY_TOLERANCE = 0.10f;
    private static final float DODGE_HORIZON = 0.5f;
    private static final float DODGE_COOLDOWN = 0.6f;

    private final GameWorld world;

    public EnemyAI(GameWorld world) {
        this.world = world;
    }

    public void update(float dt) {
        if (world.player.dead) return;
        for (Shooter enemy : world.enemies) {
            if (!enemy.dead && !enemy.isSpawning()) updateEnemy(enemy, dt);
        }
    }

    private void updateEnemy(Shooter enemy, float dt) {
        if (enemy.dodgeCooldown > 0f) enemy.dodgeCooldown -= dt;

        // a player bullet is about to hit: fire in a direction whose recoil
        // shoves the enemy off the bullet's path (the shot also heads back
        // toward the player, so the dodge doubles as a counter-attack)
        if (enemy.fireCooldown <= 0f && enemy.dodgeCooldown <= 0f) {
            Bullet threat = findIncomingThreat(enemy);
            if (threat != null) {
                enemy.angle = dodgeAimAngle(enemy, threat);
                world.fire(enemy);
                enemy.dodgeCooldown = DODGE_COOLDOWN;
                return;
            }
        }

        // resting on the ground: the muzzle doesn't spin, so it can't align
        // to aim; fire every so often just to hop back into the air
        if (enemy.grounded) {
            if (enemy.fireCooldown <= 0f) {
                world.fire(enemy);
                enemy.fireCooldown = MathUtils.random(0.7f, 1.1f);
            }
            return;
        }

        float dx = world.player.x - enemy.x;
        float dy = world.player.y - enemy.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // lead the target: predict where the player will be when the bullet
        // arrives, accounting for gravity on the player and the world bounds
        float flight = dist / Shooter.BULLET_SPEED;
        float px = world.player.x + world.player.vx * flight;
        float py = world.player.y + world.player.vy * flight
                - 0.5f * Shooter.GRAVITY * flight * flight;
        px = MathUtils.clamp(px, 20f, WorldConfig.WORLD_W - 20f);
        py = MathUtils.clamp(py, WorldConfig.GROUND_TOP + 18f, WorldConfig.WORLD_H - 18f);
        float desired = MathUtils.atan2(py - enemy.y, px - enemy.x);

        // drift back toward the player when too far away
        if (dist > 460f) desired += MathUtils.clamp((dist - 460f) / 800f, 0f, 0.5f) * MathUtils.PI;

        // the enemy muzzle spins like the player's; fire only when it sweeps
        // across the predicted aim, in short bursts
        float diff = desired - enemy.angle;
        while (diff > MathUtils.PI) diff -= MathUtils.PI2;
        while (diff < -MathUtils.PI) diff += MathUtils.PI2;

        if (enemy.fireCooldown <= 0f && Math.abs(diff) < ENEMY_TOLERANCE) {
            world.fire(enemy);
            if (enemy.burst <= 0) enemy.burst = 3;
            enemy.burst--;
            enemy.fireCooldown = enemy.burst > 0 ? 0.15f : MathUtils.random(0.9f, 1.5f);
        }
    }

    /** Nearest player bullet that will hit the given enemy within the dodge horizon, or null. */
    private Bullet findIncomingThreat(Shooter enemy) {
        Bullet best = null;
        float bestT = Float.MAX_VALUE;
        for (int i = 0; i < world.bullets.size; i++) {
            Bullet b = world.bullets.get(i);
            if (b.dead || !b.owner.isPlayer) continue;
            float t = bulletTimeToEnemy(b, enemy);
            if (t >= 0f && t < DODGE_HORIZON && t < bestT) {
                best = b;
                bestT = t;
            }
        }
        return best;
    }

    /** Time until a bullet enters the enemy's hitbox, or -1 if it never does. */
    private float bulletTimeToEnemy(Bullet b, Shooter enemy) {
        return bulletTimeToEnemy(b.x, b.y, b.vx, b.vy, enemy, CollisionSystem.BULLET_HIT_RADIUS);
    }

    /**
     * Static core of {@link #bulletTimeToEnemy(Bullet, Shooter)} so the
     * ray-vs-hitbox math is unit-testable without a live GameWorld.
     */
    static float bulletTimeToEnemy(float bx, float by, float bvx, float bvy,
                                   Shooter enemy, float hitRadius) {
        float cos = MathUtils.cos(enemy.angle);
        float sin = MathUtils.sin(enemy.angle);
        float dx = bx - enemy.x, dy = by - enemy.y;
        float lx = cos * dx + sin * dy;
        float ly = -sin * dx + cos * dy;
        float lvx = cos * bvx + sin * bvy;
        float lvy = -sin * bvx + cos * bvy;
        float best = -1f;
        for (float[] hb : Shooter.HITBOXES) {
            float t = Geometry.hitTimeAlong(lx, ly, lvx, lvy,
                    hb[0] - hitRadius, hb[1] - hitRadius,
                    hb[2] + hitRadius, hb[3] + hitRadius);
            if (t >= 0f && (best < 0f || t < best)) best = t;
        }
        return best;
    }

    /**
     * Direction to fire so that the recoil shoves the enemy perpendicular to
     * the incoming bullet's path, away from it.
     */
    private float dodgeAimAngle(Shooter enemy, Bullet b) {
        return dodgeAimAngle(enemy.x, enemy.y, b.x, b.y, b.vx, b.vy);
    }

    /**
     * Static core of {@link #dodgeAimAngle(Shooter, Bullet)} so the aim math
     * is unit-testable without a live GameWorld.
     */
    static float dodgeAimAngle(float ex, float ey, float bx, float by, float bvx, float bvy) {
        float pushX = -bvy, pushY = bvx;
        float len = (float) Math.sqrt(pushX * pushX + pushY * pushY);
        if (len < 0.0001f) return 0f;
        pushX /= len;
        pushY /= len;
        float rx = ex - bx, ry = ey - by;
        if (pushX * rx + pushY * ry < 0f) {
            pushX = -pushX;
            pushY = -pushY;
        }
        return MathUtils.atan2(-pushY, -pushX);
    }
}
