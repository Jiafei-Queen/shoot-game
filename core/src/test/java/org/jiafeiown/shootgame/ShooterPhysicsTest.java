package org.jiafeiown.shootgame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;

/** Tests for {@link Shooter#update} physics and {@link Shooter#bulletHits}. */
class ShooterPhysicsTest {

    private static final float EPS = 1e-3f;
    private static final float DT = 1f / 60f;
    private static final float HALF_W = 51f;
    private static final float HALF_H = 21f;

    private static Shooter shooter(float x, float y, float vx, float vy) {
        Color c = new Color(1f, 1f, 1f, 1f);
        Shooter s = new Shooter(true, x, y, 100, 0.28f, c, c, c, c, c, c);
        s.vx = vx;
        s.vy = vy;
        return s;
    }

    // ---- physics integration ----

    @Test
    void gravityPullsDown() {
        Shooter s = shooter(400f, 500f, 0f, 0f);
        s.update(DT);
        assertEquals(-Shooter.GRAVITY * DT, s.vy, EPS);   // vy -= GRAVITY*dt
        assertEquals(499.75f, s.y, EPS);                  // y += vy*dt
    }

    @Test
    void groundClampsAndZeroesFall() {
        Shooter s = shooter(400f, GameWorld.GROUND_TOP + HALF_H + 1f, 0f, -100f);
        s.update(DT);
        assertEquals(GameWorld.GROUND_TOP + HALF_H, s.y, EPS);
        assertEquals(0f, s.vy, EPS);
        assertTrue(s.grounded);
    }

    @Test
    void ceilingClampsAndZeroesRise() {
        Shooter s = shooter(400f, GameWorld.WORLD_H - HALF_H - 1f, 0f, 100f);
        s.update(DT);
        assertEquals(GameWorld.WORLD_H - HALF_H, s.y, EPS);
        assertEquals(0f, s.vy, EPS);
    }

    @Test
    void leftWallClampsAndZeroesHorizontalSpeed() {
        Shooter s = shooter(HALF_W - 1f, GameWorld.GROUND_TOP + HALF_H, -100f, 0f);
        s.update(DT);
        assertEquals(HALF_W, s.x, EPS);
        assertEquals(0f, s.vx, EPS);
    }

    @Test
    void groundDragAppliesWhenGrounded() {
        Shooter s = shooter(400f, GameWorld.GROUND_TOP + HALF_H, 100f, 0f);
        s.update(DT);
        assertEquals(100f * (1f - 2.6f * DT), s.vx, EPS);
    }

    @Test
    void airDragAppliesWhenAirborne() {
        Shooter s = shooter(400f, 500f, 100f, 0f);
        s.update(DT);
        assertEquals(100f * (1f - 0.2f * DT), s.vx, EPS);
    }

    @Test
    void muzzleSpinsOnlyWhileAirborne() {
        Shooter air = shooter(400f, 500f, 0f, 0f);
        air.spin = 2.2f;
        air.update(DT);
        assertEquals(2.2f * DT, air.angle, EPS);

        Shooter ground = shooter(400f, GameWorld.GROUND_TOP + HALF_H, 0f, 0f);
        ground.spin = 2.2f;
        ground.update(DT);
        assertEquals(0f, ground.angle, EPS);
    }

    @Test
    void muzzleAngleWrapsAroundTwoPi() {
        Shooter s = shooter(400f, 500f, 0f, 0f);
        s.spin = 2.2f;
        s.angle = MathUtils.PI2 - 0.01f;
        s.update(DT);
        assertEquals(MathUtils.PI2 - 0.01f + 2.2f * DT - MathUtils.PI2, s.angle, EPS);
    }

    @Test
    void halfExtentsDependOnAngle() {
        Shooter s = shooter(0f, 0f, 0f, 0f);
        s.angle = 0f;
        assertEquals(HALF_W, s.halfWidth(), EPS);
        assertEquals(HALF_H, s.halfHeight(), EPS);
        s.angle = MathUtils.PI / 2f;
        assertEquals(HALF_H, s.halfWidth(), EPS);
        assertEquals(HALF_W, s.halfHeight(), EPS);
    }

    // ---- hitbox tests (angle = 0, shooter centered at origin) ----

    @Test
    void bulletHitsBody() {
        assertTrue(shooter(0f, 0f, 0f, 0f).bulletHits(50f, 0f, 4f));
    }

    @Test
    void bulletHitsBarrel() {
        assertTrue(shooter(0f, 0f, 0f, 0f).bulletHits(100f, 0f, 4f));
    }

    @Test
    void bulletHitsBackGrip() {
        assertTrue(shooter(0f, 0f, 0f, 0f).bulletHits(10f, -30f, 4f));
    }

    @Test
    void bulletMissesClearOfGun() {
        assertFalse(shooter(0f, 0f, 0f, 0f).bulletHits(0f, 50f, 4f));
    }
}
