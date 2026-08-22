package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the enemy spawn transition: a freshly spawned enemy is frozen in
 * place for {@link Shooter#SPAWN_TIME} seconds (no physics, no AI actions),
 * takes no damage while materializing, and fades in along the spawn alpha
 * curve. Bullets also pass through it without being consumed.
 */
public class EnemySpawnTest {

    private static final float DT = 1f / 60f;
    private static final Color C = new Color(1f, 1f, 1f, 1f);

    private Shooter newEnemy(float x, float y) {
        return new Shooter(false, x, y, 100, 1.15f, C, C, C, C, C, C);
    }

    @Test
    void spawnDefaultsToInstantMaterialization() {
        // the player (and any shooter not explicitly armed by RoundManager)
        // spawns fully solid
        Shooter s = newEnemy(100f, 200f);
        assertFalse(s.isSpawning());
        assertEquals(1f, s.spawnAlpha(), 0.0001f);
    }

    @Test
    void spawningEnemyIsFrozenForOneSecond() {
        Shooter e = newEnemy(300f, WorldConfig.GROUND_TOP + 80f);
        e.spawnTimer = Shooter.SPAWN_TIME;
        assertTrue(e.isSpawning());

        float x = e.x, y = e.y;
        int frames = 0;
        while (e.isSpawning() && frames++ < 120) e.update(DT);
        assertFalse(e.isSpawning());
        // gravity, drag and spin are all suspended while materializing:
        // position and angle stay exactly where they were spawned
        assertEquals(x, e.x, 0.0001f);
        assertEquals(y, e.y, 0.0001f);
        assertEquals(0f, e.vy, 0.0001f);

        // ...and physics resumes (the enemy starts falling toward the ground)
        e.update(DT);
        assertTrue(e.y < y);
    }

    @Test
    void spawnAlphaEasesFromZeroToOne() {
        Shooter e = newEnemy(0f, 0f);
        e.spawnTimer = Shooter.SPAWN_TIME;
        assertEquals(0f, e.spawnAlpha(), 0.0001f);
        e.spawnTimer -= Shooter.SPAWN_TIME * 0.5f;
        assertEquals(0.5f, e.spawnAlpha(), 0.01f); // smoothstep midpoint
        e.spawnTimer = 0f;
        assertEquals(1f, e.spawnAlpha(), 0.0001f);
    }

    @Test
    void spawningEnemyTakesNoDamage() {
        GameWorld world = new GameWorld();
        world.player = new Shooter(true, 100f, 200f, Shooter.BASE_HP, 0.28f,
                C, C, C, C, C, C);
        world.rounds.reset();
        Shooter e = world.enemies.first();
        assertTrue(e.isSpawning());

        int hpBefore = e.hp;
        e.takeDamage(50, 1f, 0f);
        assertEquals(hpBefore, e.hp);
        assertFalse(e.dead);

        // after materializing the same hit lands normally
        e.spawnTimer = 0f;
        e.takeDamage(50, 1f, 0f);
        assertEquals(hpBefore - 50, e.hp);
    }

    @Test
    void bulletsPassThroughSpawningEnemies() {
        GameWorld world = new GameWorld();
        world.player = new Shooter(true, 100f, 200f, Shooter.BASE_HP, 0.28f,
                C, C, C, C, C, C);
        world.player.angle = 0f; // firing horizontally to the right
        world.player.x = 200f;
        world.rounds.reset();

        // put every enemy far above the bullet's path except one that sits
        // right on it, still materializing
        Shooter target = world.enemies.first();
        target.x = 600f;
        target.y = 200f + Shooter.MUZZLE_OFFSET * 0f; // on the bullet's line
        target.y = 200f;
        for (int i = 1; i < world.enemies.size; i++) {
            world.enemies.get(i).y = WorldConfig.WORLD_H - 40f;
        }
        assertTrue(target.isSpawning());
        int hpBefore = target.hp;

        world.fire(world.player);
        // simulate until the bullet has crossed the whole field
        for (int i = 0; i < 120 && !world.bullets.isEmpty(); i++) {
            world.player.update(DT);
            for (Shooter e : world.enemies) e.update(DT);
            for (Bullet b : world.bullets) b.prevX = b.x;
            world.collision.update();
        }
        // no damage, no kill: the bullet flew through untouched
        assertEquals(hpBefore, target.hp);
        assertFalse(target.dead);
    }

    @Test
    void roundRestartRespawnsEnemiesWithSpawnEffect() {
        GameWorld world = new GameWorld();
        world.player = new Shooter(true, 100f, 200f, Shooter.BASE_HP, 0.28f,
                C, C, C, C, C, C);
        world.rounds.reset();
        for (Shooter e : world.enemies) {
            assertTrue(e.isSpawning());
            assertEquals(Shooter.SPAWN_TIME, e.spawnTimer, 0.0001f);
        }
        // each freshly spawned enemy contributes its materialize particles:
        // 16 converging motes + 10 outward puffs
        assertEquals(26 * world.enemies.size, world.fx.particles.size);
    }
}
