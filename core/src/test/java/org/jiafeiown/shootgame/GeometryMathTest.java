package org.jiafeiown.shootgame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure geometry helpers in {@link GameWorld}:
 * {@link GameWorld#hitTimeAlong} (ray-vs-AABB) and
 * {@link GameWorld#segSegDistSq} (segment-vs-segment distance).
 * Both are plain math with no Gdx state, so they run headless.
 */
class GeometryMathTest {

    private static final float EPS = 1e-3f;

    // ---- hitTimeAlong: time until (px,py)+(vx,vy)*t enters the box, or -1 ----

    @Test
    void rayHitsBoxStraightOn() {
        // (0,0)+t*(1,0) enters box [5,10]x[-2,2] at t=5.
        assertEquals(5f, GameWorld.hitTimeAlong(0f, 0f, 1f, 0f, 5f, -2f, 10f, 2f), EPS);
    }

    @Test
    void rayStartingInsideBoxReturnsZero() {
        assertEquals(0f, GameWorld.hitTimeAlong(6f, 0f, 1f, 0f, 5f, -2f, 10f, 2f), EPS);
    }

    @Test
    void rayMovingAwayNeverHits() {
        assertEquals(-1f, GameWorld.hitTimeAlong(0f, 0f, -1f, 0f, 5f, -2f, 10f, 2f), EPS);
    }

    @Test
    void rayParallelAndAboveBoxNeverHits() {
        assertEquals(-1f, GameWorld.hitTimeAlong(0f, 10f, 1f, 0f, 5f, -2f, 10f, 2f), EPS);
    }

    @Test
    void rayHittingFromBelow() {
        assertEquals(5f, GameWorld.hitTimeAlong(5f, -5f, 0f, 1f, 0f, 0f, 10f, 2f), EPS);
    }

    @Test
    void diagonalRayHitsBox() {
        assertEquals(10f, GameWorld.hitTimeAlong(0f, 0f, 1f, 1f, 10f, 10f, 12f, 12f), EPS);
    }

    @Test
    void stationaryRayInsideBoxIsZero() {
        assertEquals(0f, GameWorld.hitTimeAlong(6f, 0f, 0f, 0f, 5f, -2f, 10f, 2f), EPS);
    }

    @Test
    void stationaryRayOutsideBoxNeverHits() {
        assertEquals(-1f, GameWorld.hitTimeAlong(20f, 0f, 0f, 0f, 5f, -2f, 10f, 2f), EPS);
    }

    // ---- segSegDistSq: squared distance between two segments ----

    @Test
    void crossingSegmentsAreZeroDistance() {
        assertEquals(0f, GameWorld.segSegDistSq(0f, 0f, 2f, 2f, 0f, 2f, 2f, 0f), EPS);
    }

    @Test
    void parallelSegmentsDistanceSquared() {
        // y=0 vs y=2 → distance 2 → squared 4.
        assertEquals(4f, GameWorld.segSegDistSq(0f, 0f, 2f, 0f, 0f, 2f, 2f, 2f), EPS);
    }

    @Test
    void pointToSegmentDistanceSquared() {
        // Point (0,0) to segment x=3, y in [0,4] → distance 3 → squared 9.
        assertEquals(9f, GameWorld.segSegDistSq(0f, 0f, 0f, 0f, 3f, 0f, 3f, 4f), EPS);
    }

    @Test
    void coincidentSegmentsAreZeroDistance() {
        assertEquals(0f, GameWorld.segSegDistSq(0f, 0f, 2f, 2f, 0f, 0f, 2f, 2f), EPS);
    }

    @Test
    void perpendicularEndToEndDistanceSquared() {
        // (0,0)-(1,0) and (1,1)-(1,2): closest points (1,0) and (1,1) → 1.
        assertEquals(1f, GameWorld.segSegDistSq(0f, 0f, 1f, 0f, 1f, 1f, 1f, 2f), EPS);
    }

    @Test
    void segmentsSharingACornerAreZeroDistance() {
        // (0,0)-(2,2) and (2,0)-(2,2) share the corner (2,2).
        assertEquals(0f, GameWorld.segSegDistSq(0f, 0f, 2f, 2f, 2f, 0f, 2f, 2f), EPS);
    }
}
