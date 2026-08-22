package org.jiafeiown.shootgame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression: a bullet must never damage the shooter who fired it, at any
 * frame rate, and cross-entity hits must still land.
 *
 * <p>The bullet spawns at the muzzle, 102px from the shooter's centre, which
 * is inside the firer's own front-grip hitbox (local x up to 105, +4 radius
 * padding). The old point-in-hitbox collision check tested the bullet's
 * position one frame after firing, so whether the own bullet self-hit depended
 * on how far it travelled in that frame — i.e. on the frame rate: at 120fps+
 * (uncapped Android/Web displays, never the 60fps-capped desktop) the bullet
 * was still inside the hitbox and the firer took instant self-damage.
 *
 * <p>Simulates the exact update ordering of {@link GameWorld#update} — player
 * moves, then bullets move, then {@link CollisionSystem#update()} — with the
 * player firing at the end of a frame, exactly as {@link GameWorld#render()}
 * does via {@code handleInput()}.
 */
public class OwnBulletNoSelfHitTest {

    private static final float[] FPS = {30f, 60f, 90f, 120f, 144f, 165f, 240f};
    private static final float[] ANGLES_DEG = {-90f, 0f, 45f, 90f, 135f};

    private static GameWorld newWorld(float angle) {
        GameWorld world = new GameWorld();
        Shooter player = new Shooter(true, 400f, WorldConfig.GROUND_TOP + 160f, Shooter.BASE_HP, 0.14f,
                Palette.playerBody, Palette.playerBarrel, Palette.playerGrip, Palette.playerSlide,
                Palette.playerBullet, Palette.playerBulletCore);
        player.angle = angle;
        player.spin = 2.2f;
        player.invincibleTime = 0f; // outside the round-start shield
        world.player = player;
        return world;
    }

    private static Shooter enemyAt(float x, float y, float angle) {
        Shooter e = new Shooter(false, x, y, Shooter.BASE_HP, 1f,
                Palette.enemyBody, Palette.enemyBarrel, Palette.enemyGrip, Palette.enemySlide,
                Palette.enemyBullet, Palette.enemyBulletCore);
        e.angle = angle;
        e.spin = 0f;
        return e;
    }

    /** One physics frame: the entity/bullet moves and collisions run. */
    private static void step(GameWorld world, float dt) {
        world.player.update(dt);
        for (Shooter e : world.enemies) e.update(dt);
        for (int i = world.bullets.size - 1; i >= 0; i--) {
            Bullet b = world.bullets.get(i);
            b.update(dt);
            if (b.dead) world.bullets.removeIndex(i);
        }
        world.collision.update();
    }

    @Test
    void ownBulletNeverHitsFirerAcrossFrameRates() {
        for (float fps : FPS) {
            for (float angleDeg : ANGLES_DEG) {
                GameWorld world = newWorld((float) Math.toRadians(angleDeg));
                world.fire(world.player); // end of a frame, as handleInput does
                float dt = 1f / fps;
                for (int f = 0; f < 10; f++) {
                    step(world, dt);
                    assertEquals(Shooter.BASE_HP, world.player.hp,
                            "player hit by own bullet at " + Math.round(fps) + "fps angle " + angleDeg + "°");
                }
            }
        }
    }

    @Test
    void enemyOwnBulletNeverHitsEnemy() {
        for (float fps : FPS) {
            GameWorld world = newWorld(0f);
            world.player.x = 40f;
            world.player.y = 1200f; // far from the enemy's shot
            Shooter enemy = enemyAt(700f, WorldConfig.GROUND_TOP + 160f, (float) Math.toRadians(135f));
            enemy.invincibleTime = 0f;
            world.enemies.add(enemy);
            world.fire(enemy);
            float dt = 1f / fps;
            for (int f = 0; f < 10; f++) {
                step(world, dt);
                assertEquals(Shooter.BASE_HP, enemy.hp,
                        "enemy hit by own bullet at " + Math.round(fps) + "fps");
            }
        }
    }

    @Test
    void playerBulletStillHitsEnemyAtRange() {
        GameWorld world = newWorld(0f); // fires horizontally toward +x
        Shooter enemy = enemyAt(700f, WorldConfig.GROUND_TOP + 160f, (float) Math.toRadians(180f));
        world.enemies.add(enemy);
        world.fire(world.player);
        float dt = 1f / 60f;
        for (int f = 0; f < 120 && enemy.hp == Shooter.BASE_HP && !enemy.dead; f++) {
            step(world, dt);
        }
        assertTrue(enemy.hp < Shooter.BASE_HP || enemy.dead,
                "player bullet should still damage the enemy");
    }

    @Test
    void enemyBulletStillHitsPlayerAtRange() {
        GameWorld world = newWorld(0f);
        Shooter enemy = enemyAt(700f, WorldConfig.GROUND_TOP + 160f, (float) Math.toRadians(180f));
        world.enemies.add(enemy);
        world.fire(enemy); // fires horizontally toward the player at -x
        float dt = 1f / 60f;
        for (int f = 0; f < 120 && world.player.hp == Shooter.BASE_HP; f++) {
            step(world, dt);
        }
        assertTrue(world.player.hp < Shooter.BASE_HP,
                "enemy bullet should still damage the player");
    }
}
