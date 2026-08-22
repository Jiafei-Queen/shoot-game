package org.jiafeiown.shootgame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link RoundManager#restartRound()} restores the current
 * round's opening scene exactly: player position/orientation/velocity/hp
 * snapshotted when the round started, enemies respawned at their opening
 * spots with full hp, the field emptied of bullets and effects, and the
 * match stats rolled back to the round-start values.
 *
 * <p>{@link GameWorld} is instantiable headlessly — rendering objects are only
 * created in {@code create()}, which this test never calls.
 */
public class RoundManagerRestartTest {

    private GameWorld newWorld() {
        GameWorld world = new GameWorld();
        world.player = new Shooter(true, 100f, 200f, Shooter.BASE_HP, 0.28f,
                Palette.playerBody, Palette.playerBarrel, Palette.playerGrip, Palette.playerSlide,
                Palette.playerBullet, Palette.playerBulletCore);
        world.player.angle = 1.2f;
        world.player.vx = 30f;
        world.player.vy = -50f;
        world.player.hp = 42;
        return world;
    }

    @Test
    void restartRoundRestoresRoundStartScene() {
        GameWorld world = newWorld();
        // round 1 starts: the snapshot is taken from the player's current state
        world.rounds.reset();
        float startX = world.player.x, startY = world.player.y, startAngle = world.player.angle;
        float startVx = world.player.vx, startVy = world.player.vy;
        int startHp = world.player.hp;
        Shooter enemy = world.enemies.first();
        float enemyStartX = enemy.x, enemyStartAngle = enemy.angle, enemyStartHp = enemy.hp;
        int enemyCount = world.enemies.size;

        // the player fights on: moves, turns, takes damage, and bullets/effects
        // and enemy positions change; stats accrue
        world.player.x = 700f;
        world.player.y = 900f;
        world.player.angle = 0.3f;
        world.player.vx = -12f;
        world.player.vy = 220f;
        world.player.hp = 5;
        enemy.x = 500f;
        enemy.angle = 2f;
        enemy.hp = 30;
        world.bullets.add(new Bullet(world.player, 300f, 300f, 1f));
        world.fx.burst(200f, 200f, Palette.sparkCol, 8, 240f);
        assertTrue(world.bullets.size > 0);
        assertTrue(world.fx.particles.size > 0);
        world.rounds.kills = 3;
        world.rounds.damageDealt = 55;

        world.rounds.restartRound();

        // player back to the exact round-start state
        assertEquals(startX, world.player.x, 0.001f);
        assertEquals(startY, world.player.y, 0.001f);
        assertEquals(startAngle, world.player.angle, 0.001f);
        assertEquals(startVx, world.player.vx, 0.001f);
        assertEquals(startVy, world.player.vy, 0.001f);
        assertEquals(startHp, world.player.hp);

        // field emptied: no bullets, no particles/muzzle flashes
        assertEquals(0, world.bullets.size);
        assertEquals(0, world.fx.particles.size);

        // enemies respawned fresh at their opening spots with full hp
        assertEquals(enemyCount, world.enemies.size);
        Shooter respawned = world.enemies.first();
        assertEquals(enemyStartX, respawned.x, 0.001f);
        assertEquals(enemyStartAngle, respawned.angle, 0.001f);
        assertEquals(enemyStartHp, respawned.hp);
        assertEquals(enemyStartHp, respawned.maxHp);

        // match stats rolled back to the round-start values (0 for round 1)
        assertEquals(0, world.rounds.kills);
        assertEquals(0, world.rounds.damageDealt);

        // the fresh round is live and shielded, with the banner counting down
        assertEquals(GameState.PLAYING, world.rounds.state);
        assertTrue(world.player.invincibleTime > 0f);
        assertTrue(world.rounds.roundBannerTime > 0f);
    }

    @Test
    void restartingTwiceReturnsToTheSameOpening() {
        GameWorld world = newWorld();
        world.rounds.reset();
        float startX = world.player.x, startY = world.player.y;
        int startHp = world.player.hp;

        world.player.x = 300f;
        world.player.y = 600f;
        world.player.hp = 20;
        world.rounds.restartRound();
        assertEquals(startX, world.player.x, 0.001f);
        assertEquals(startY, world.player.y, 0.001f);
        assertEquals(startHp, world.player.hp);

        // second restart still lands on the original opening, not the in-between state
        world.player.x = 555f;
        world.player.y = 777f;
        world.player.hp = 1;
        world.rounds.restartRound();
        assertEquals(startX, world.player.x, 0.001f);
        assertEquals(startY, world.player.y, 0.001f);
        assertEquals(startHp, world.player.hp);
    }
}
