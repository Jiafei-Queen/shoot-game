package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the enemy death dissolve-out: a defeated enemy lingers on screen
 * for {@link Shooter#DEATH_TIME} seconds while fading away (with particles
 * spawned at the moment of death), the round does not advance until every
 * dissolve-out has finished, and the player still disappears instantly.
 */
public class EnemyDeathTest {

    private static final float DT = 1f / 60f;
    private static final Color C = new Color(1f, 1f, 1f, 1f);

    private GameWorld newWorld() {
        GameWorld world = new GameWorld();
        world.player = new Shooter(true, 100f, 200f, Shooter.BASE_HP, 0.28f,
                C, C, C, C, C, C);
        world.rounds.reset();
        return world;
    }

    @Test
    void lethalDamageArmsDeathTimerForEnemiesOnly() {
        GameWorld world = newWorld();

        Shooter e = world.enemies.first();
        e.spawnTimer = 0f; // skip the materialize freeze so the hit can land
        e.takeDamage(e.hp + 1, 0f, 1f);
        assertTrue(e.dead);
        assertTrue(e.isDying());
        assertEquals(Shooter.DEATH_TIME, e.deathTimer, 0.0001f);

        // the player vanishes immediately on defeat: no dissolve (drop the
        // round-start shield first, it would absorb the hit)
        world.player.invincibleTime = 0f;
        world.player.takeDamage(world.player.hp + 1, 0f, 1f);
        assertTrue(world.player.dead);
        assertFalse(world.player.isDying());
        assertEquals(0f, world.player.deathTimer, 0.0001f);
    }

    @Test
    void deathFadeRunsHalfASecondThenStops() {
        Shooter e = new Shooter(false, 300f, 200f, 100, 1.15f, C, C, C, C, C, C);
        e.spawnTimer = 0f;
        e.takeDamage(e.hp + 1, 0f, 1f);

        // mid-dissolve the corpse is semi-transparent and still ticking down
        e.update(DT * 25f); // ~0.42s
        assertTrue(e.isDying());
        float mid = e.deathAlpha();
        assertTrue(mid > 0f && mid < 1f);

        // past DEATH_TIME the animation is over and stays over
        for (int i = 0; i < 30; i++) e.update(DT);
        assertFalse(e.isDying());
        assertEquals(0f, e.deathTimer, 0.0001f);
        assertEquals(0f, e.deathAlpha(), 0.0001f);
    }

    @Test
    void deathEffectsSpawnParticlesAtTheMomentOfDeath() {
        GameWorld world = newWorld();
        world.fx.clear();
        Shooter e = world.enemies.first();
        e.spawnTimer = 0f;
        // fired by CollisionSystem right after a lethal hit lands
        world.fx.deathEffects(e);
        // burst (16 + 10) plus upward embers (8)
        assertEquals(34, world.fx.particles.size);
    }

    @Test
    void roundDoesNotAdvanceWhileACorpseIsStillDissolving() {
        GameWorld world = newWorld();
        int roundBefore = world.rounds.round;

        // wipe out the whole wave in one frame
        for (Shooter e : world.enemies) {
            e.spawnTimer = 0f;
            e.takeDamage(e.hp + 1, 0f, 1f);
        }
        world.rounds.update();
        assertEquals(roundBefore, world.rounds.round, "round must wait for the dissolve-outs");

        // let every death animation finish (plus slack for float error), then
        // the round advances and heals the player
        for (int i = 0; i < 60; i++) {
            world.player.update(DT);
            for (Shooter e : world.enemies) e.update(DT);
            world.rounds.update();
        }
        assertEquals(roundBefore + 1, world.rounds.round);
    }
}
