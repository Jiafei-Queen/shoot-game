package org.jiafeiown.shootgame;

import com.badlogic.gdx.utils.Array;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Collision detection and resolution: bullet-vs-shooter hits, bullet-vs-bullet
 * shattering and shooter-vs-shooter overlap separation.
 *
 * <p>Reads world state (bullets, player, enemies) through its {@link GameWorld}
 * reference, applies damage through the entities, and spawns impact effects via
 * {@link GameWorld#burst(float, float, com.badlogic.gdx.graphics.Color, int, float)}.
 */
public class CollisionSystem {

    private static final Logger log = LogManager.getLogger(CollisionSystem.class);

    /** Bullet-vs-shooter hitbox padding; also used by {@link EnemyAI}'s intercept math. */
    static final float BULLET_HIT_RADIUS = 4f;
    private static final float BULLET_COLLIDE_RADIUS = 12f;

    private final GameWorld world;

    public CollisionSystem(GameWorld world) {
        this.world = world;
    }

    /** Runs all collision passes for one frame. Called after entity updates. */
    public void update() {
        checkBulletHits();
        checkBulletCollisions();
        resolveShooterCollisions();
    }

    private void checkBulletHits() {
        Array<Bullet> bullets = world.bullets;
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            if (b.dead) {
                bullets.removeIndex(i);
                continue;
            }
            Shooter player = world.player;
            if (!player.dead && player.bulletHits(b.x, b.y, BULLET_HIT_RADIUS)) {
                b.dead = true;
                if (player.invincibleTime > 0f) {
                    // round-start shield absorbs the bullet
                    world.burst(b.x, b.y, Palette.shieldCol, 8, 170f);
                    log.debug("Bullet absorbed by invincibility shield");
                } else {
                    int hpBefore = player.hp;
                    player.takeDamage(b.owner.damage, b.nx, b.ny);
                    log.debug("Player hit for {} damage (hp {} → {})", b.owner.damage, hpBefore, player.hp);
                    world.burst(b.x, b.y, Palette.playerHit, 10, 190f);
                }
            } else {
                for (Shooter e : world.enemies) {
                    if (e.dead) continue;
                    if (e.bulletHits(b.x, b.y, BULLET_HIT_RADIUS)) {
                        b.dead = true;
                        e.takeDamage(b.owner.damage, b.nx, b.ny);
                        if (b.owner.isPlayer) world.damageDealt += b.owner.damage;
                        world.burst(b.x, b.y, Palette.enemyHit, 10, 190f);
                        if (e.dead) {
                            world.kills++;
                            log.debug("Enemy #{} killed | kills={}, damageDealt={}",
                                    world.enemies.indexOf(e, true), world.kills, world.damageDealt);
                        }
                        break;
                    }
                }
            }
            if (b.dead) bullets.removeIndex(i);
        }
    }

    /** Bullets that meet shatter each other: both vanish in a spark burst. */
    private void checkBulletCollisions() {
        float rr = BULLET_COLLIDE_RADIUS * BULLET_COLLIDE_RADIUS;
        Array<Bullet> bullets = world.bullets;
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet a = bullets.get(i);
            if (a.dead) {
                bullets.removeIndex(i);
                continue;
            }
            for (int j = i - 1; j >= 0; j--) {
                Bullet b = bullets.get(j);
                if (b.dead) continue;
                if (Geometry.segSegDistSq(a.prevX, a.prevY, a.x, a.y,
                        b.prevX, b.prevY, b.x, b.y) <= rr) {
                    a.dead = true;
                    b.dead = true;
                    float mx = (a.x + b.x) * 0.5f;
                    float my = (a.y + b.y) * 0.5f;
                    world.burst(mx, my, a.color, 8, 240f);
                    world.burst(mx, my, b.color, 8, 240f);
                    break;
                }
            }
            if (a.dead) bullets.removeIndex(i);
        }
    }

    private void resolveShooterCollisions() {
        Shooter player = world.player;
        if (player.dead) return;
        for (Shooter enemy : world.enemies) {
            if (enemy.dead) continue;
            resolveOverlap(player, enemy);
        }
    }

    /**
     * Pushes two overlapping shooters apart along the axis of least
     * penetration and zeroes their velocity on that axis. Static so it is
     * unit-testable without a live GameWorld.
     */
    static void resolveOverlap(Shooter a, Shooter b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float ox = (a.halfWidth() + b.halfWidth()) - Math.abs(dx);
        float oy = (a.halfHeight() + b.halfHeight()) - Math.abs(dy);
        if (ox > 0f && oy > 0f) {
            if (ox < oy) {
                float dir = dx < 0f ? -1f : 1f;
                a.x += dir * ox * 0.5f;
                b.x -= dir * ox * 0.5f;
                a.vx = 0f;
                b.vx = 0f;
            } else {
                float dir = dy < 0f ? -1f : 1f;
                a.y += dir * oy * 0.5f;
                b.y -= dir * oy * 0.5f;
                a.vy = 0f;
                b.vy = 0f;
            }
        }
    }
}
