package org.jiafeiown.shootgame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

/** Tests for {@link GameWorld#resolveOverlap} (shooter-vs-shooter separation). */
class CollisionResolutionTest {

    private static final float EPS = 1e-3f;
    private static final float HALF_W = 51f;
    private static final float HALF_H = 21f;

    private static Shooter shooterAt(float x, float y) {
        Color c = new Color(1f, 1f, 1f, 1f);
        return new Shooter(true, x, y, 100, 0.28f, c, c, c, c, c, c);
    }

    @Test
    void horizontalOverlapResolvesOnX() {
        Shooter a = shooterAt(0f, 0f);
        Shooter b = shooterAt(92f, 0f); // 2*HALF_W - 92 = 10 units of x-overlap
        GameWorld.resolveOverlap(a, b);
        // Pushed apart so their x-extents just touch: separation = 2*HALF_W.
        assertEquals(2f * HALF_W, b.x - a.x, EPS);
        assertEquals(0f, a.vx, EPS);
        assertEquals(0f, b.vx, EPS);
    }

    @Test
    void verticalOverlapResolvesOnY() {
        Shooter a = shooterAt(0f, 0f);
        Shooter b = shooterAt(0f, 40f); // 2*HALF_H - 40 = 2 units of y-overlap
        GameWorld.resolveOverlap(a, b);
        assertEquals(2f * HALF_H, b.y - a.y, EPS);
        assertEquals(0f, a.vy, EPS);
        assertEquals(0f, b.vy, EPS);
    }

    @Test
    void equalOverlapTiesBreakToTheYAxis() {
        // ox = 102-80 = 22, oy = 42-20 = 22 → else branch resolves y.
        Shooter a = shooterAt(0f, 0f);
        Shooter b = shooterAt(80f, 20f);
        GameWorld.resolveOverlap(a, b);
        assertEquals(2f * HALF_H, b.y - a.y, EPS);
        assertEquals(0f, a.vy, EPS);
        assertEquals(0f, b.vy, EPS);
    }

    @Test
    void separatedShootersAreUntouched() {
        Shooter a = shooterAt(0f, 0f);
        a.vx = 50f;
        Shooter b = shooterAt(200f, 0f);
        b.vx = -30f;
        GameWorld.resolveOverlap(a, b);
        assertEquals(0f, a.x, EPS);
        assertEquals(200f, b.x, EPS);
        assertEquals(50f, a.vx, EPS);
        assertEquals(-30f, b.vx, EPS);
    }
}
