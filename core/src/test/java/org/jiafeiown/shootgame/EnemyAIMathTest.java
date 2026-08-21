package org.jiafeiown.shootgame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;

/**
 * Tests for the pure math inside the enemy AI:
 * {@link GameWorld#bulletTimeToEnemy} (time until a bullet enters the hitbox)
 * and {@link GameWorld#dodgeAimAngle} (aim so the recoil dodges an incoming
 * shot). Both run headless — no GameWorld/Gdx needed.
 */
class EnemyAIMathTest {

    private static final float EPS = 1e-3f;
    private static final float HIT_RADIUS = 4f;
    private static final float BULLET_SPEED = Shooter.BULLET_SPEED; // 980

    private static Shooter enemyAt(float x, float y, float angle) {
        Color c = new Color(1f, 1f, 1f, 1f);
        Shooter e = new Shooter(false, x, y, 100, 1.15f, c, c, c, c, c, c);
        e.angle = angle;
        return e;
    }

    // ---- bulletTimeToEnemy ----

    @Test
    void bulletAimedAtBodyHits() {
        Shooter e = enemyAt(0f, 0f, 0f);
        float t = GameWorld.bulletTimeToEnemy(-300f, 0f, BULLET_SPEED, 0f, e, HIT_RADIUS);
        // Body hitbox starts at local x = -6, expanded by radius → -10 → t = 290/980.
        assertEquals(290f / BULLET_SPEED, t, EPS);
    }

    @Test
    void bulletMovingAwayNeverHits() {
        Shooter e = enemyAt(0f, 0f, 0f);
        assertEquals(-1f, GameWorld.bulletTimeToEnemy(-300f, 0f, -BULLET_SPEED, 0f, e, HIT_RADIUS), EPS);
    }

    @Test
    void bulletAboveGunNeverHits() {
        Shooter e = enemyAt(0f, 0f, 0f);
        assertEquals(-1f, GameWorld.bulletTimeToEnemy(-300f, 100f, BULLET_SPEED, 0f, e, HIT_RADIUS), EPS);
    }

    @Test
    void rotationIsAccountedFor() {
        // Rotated 90°: a bullet coming from below is now "in front".
        Shooter e = enemyAt(0f, 0f, MathUtils.PI / 2f);
        float t = GameWorld.bulletTimeToEnemy(0f, -300f, 0f, BULLET_SPEED, e, HIT_RADIUS);
        assertEquals(290f / BULLET_SPEED, t, EPS);
    }

    @Test
    void bulletAlreadyInsideHitsImmediately() {
        Shooter e = enemyAt(0f, 0f, 0f);
        assertEquals(0f, GameWorld.bulletTimeToEnemy(10f, 0f, BULLET_SPEED, 0f, e, HIT_RADIUS), EPS);
    }

    // ---- dodgeAimAngle: aim so recoil pushes perpendicular to the bullet path ----

    @Test
    void enemyAbovePathFiresDown() {
        // Bullet travels +x along y=0; enemy sits above it.
        float aim = GameWorld.dodgeAimAngle(0f, 100f, 0f, 0f, BULLET_SPEED, 0f);
        assertEquals(-MathUtils.PI / 2f, aim, EPS); // fire down → recoil pushes up, away
    }

    @Test
    void enemyBelowPathFiresUp() {
        float aim = GameWorld.dodgeAimAngle(0f, -100f, 0f, 0f, BULLET_SPEED, 0f);
        assertEquals(MathUtils.PI / 2f, aim, EPS);
    }

    @Test
    void enemyRightOfVerticalBulletFiresLeft() {
        // Bullet travels +y along x=0; enemy sits to its right.
        float aim = GameWorld.dodgeAimAngle(100f, 0f, 0f, 0f, 0f, BULLET_SPEED);
        assertEquals(MathUtils.PI, aim, EPS);
    }

    @Test
    void degenerateBulletVelocityFiresForward() {
        assertEquals(0f, GameWorld.dodgeAimAngle(0f, 100f, 0f, 0f, 0f, 0f), EPS);
    }
}
