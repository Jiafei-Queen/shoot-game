package org.jiafeiown.shootgame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for two UI bugs:
 *
 * <ul>
 *   <li>Starting the match from the main menu with SPACE (or a tap) must not
 *       also fire the first bullet: the very key/tap that started the game is
 *       still down on the following frames, so {@link GameWorld} arms
 *       {@code fireLatch} on every menu/tap-driven entry into play and only
 *       clears it once the player releases everything.</li>
 *   <li>The game-over stats block used to draw the total-time line and the
 *       restart hint 0.01 * WORLD_H apart, so the two texts overlapped. The
 *       baselines are now shared constants with a guaranteed gap.</li>
 * </ul>
 */
public class MenuAndGameOverFixTest {

    @Test
    void startGameArmsTheFireLatch() {
        GameWorld world = new GameWorld();
        world.toMenu();
        assertFalse(world.fireLatch);
        world.startGame(); // triggered by ENTER/SPACE press or START tap
        assertTrue(world.fireLatch,
                "the starting key/tap is still down — firing must stay latched off");
    }

    /** While latched, a still-held starting key/tap never fires. */
    @Test
    void latchedHeldInputDoesNotFire() {
        GameWorld world = new GameWorld();
        world.toMenu();
        world.startGame();
        assertFalse(world.fireHeld(true),
                "the input that started the match must not fire while held");
        assertTrue(world.fireLatch, "latch stays armed until input is released");
    }

    /**
     * The latch clears on the first frame with nothing held; from then on
     * firing behaves exactly as before (hold = keep firing).
     */
    @Test
    void latchClearsOnceInputIsReleased() {
        GameWorld world = new GameWorld();
        world.toMenu();
        world.startGame();

        // first frames: player still holds the starting key → suppressed
        assertFalse(world.fireHeld(true));
        // release frame: nothing held → latch disarms, no shot this frame
        assertFalse(world.fireHeld(false), "the release itself is not a shot");
        assertFalse(world.fireLatch);
        // afterwards: holding fires again as normal
        assertTrue(world.fireHeld(true));
    }

    /**
     * reset() alone (the R-key path) must not arm the latch — R can't fire —
     * but handleInput()'s game-over tap-to-restart branch arms it explicitly,
     * mirroring startGame().
     */
    @Test
    void plainResetLeavesLatchAlone() {
        GameWorld world = new GameWorld();
        world.toMenu();
        world.reset();
        assertFalse(world.fireLatch);
    }

    /** Total-time line and restart hint baselines need a clear vertical gap. */
    @Test
    void gameOverTimeAndHintDoNotOverlap() {
        float gap = WorldRenderer.GAME_OVER_TIME_Y - WorldRenderer.GAME_OVER_HINT_Y;
        // hint text is drawn at scale ~1.6 of a ~16px font (~26px tall); the
        // old layout had a 12.8px gap and overlapped — require much more
        assertTrue(gap >= WorldConfig.WORLD_H * 0.08f,
                "time/hint gap too small: " + gap);
    }

    /** Both game-over baselines sit inside the screen and are ordered. */
    @Test
    void gameOverBaselinesAreOrderedInsideTheScreen() {
        float h = WorldConfig.WORLD_H;
        assertEquals(h * 0.31f, WorldRenderer.GAME_OVER_TIME_Y, 0.001f);
        assertTrue(WorldRenderer.GAME_OVER_HINT_Y > 0f);
        assertTrue(WorldRenderer.GAME_OVER_HINT_Y < WorldRenderer.GAME_OVER_TIME_Y);
    }
}
