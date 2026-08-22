package org.jiafeiown.shootgame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the ESC pause wind-down: the world time scale eases 1x → 0.2x over
 * the first 0.3s and 0.2x → 0x by 1s (fully frozen), the pause screen fades in
 * linearly across the same window, and the transition resets on pause/resume.
 *
 * <p>{@link GameWorld} is instantiable headlessly — rendering objects are only
 * created in {@code create()}, which this test never calls.
 */
public class PauseTransitionTest {

    private final GameWorld world = new GameWorld();
    private final RoundManager rounds = world.rounds;

    @Test
    void timeScaleStartsAtFullSpeedAndEndsFrozen() {
        assertEquals(1f, rounds.pauseTimeScale(), 0.0001f);
        rounds.pauseTransition = 1f;
        assertEquals(0f, rounds.pauseTimeScale(), 0.0001f);
    }

    @Test
    void slowmoPhaseEasesToItsFloorByPointThreeSeconds() {
        // 0 → 0.3s: linear 1x → 0.2x
        rounds.pauseTransition = 0.15f;
        assertEquals(0.6f, rounds.pauseTimeScale(), 0.0001f); // midpoint
        rounds.pauseTransition = 0.3f;
        assertEquals(RoundManager.PAUSE_SLOWMO_SCALE, rounds.pauseTimeScale(), 0.0001f);
    }

    @Test
    void freezePhaseReachesZeroByOneSecond() {
        // 0.3s → 1s: linear 0.2x → 0x
        rounds.pauseTransition = 0.5f;
        float expected = 0.2f * (1f - (0.5f - 0.3f) / 0.7f);
        assertEquals(expected, rounds.pauseTimeScale(), 0.0001f);
        rounds.pauseTransition = 1f;
        assertEquals(0f, rounds.pauseTimeScale(), 0.0001f);
    }

    @Test
    void advanceAdvancesInRealTimeAndClampsAtZero() {
        // 0.2s in: still in the slow-mo phase
        assertEquals(1f - 0.8f * (0.2f / 0.3f), rounds.advancePauseTransition(0.2f), 0.0001f);
        // 0.5s more (0.7s total): deep in the freeze phase
        float ts = rounds.advancePauseTransition(0.5f);
        assertEquals(0.2f * (1f - 0.4f / 0.7f), ts, 0.0001f);
        // past 1s it stays pinned at 0
        assertEquals(0f, rounds.advancePauseTransition(2f), 0.0001f);
        assertEquals(0f, rounds.advancePauseTransition(2f), 0.0001f);
        assertEquals(RoundManager.PAUSE_TRANSITION_TIME, rounds.pauseTransition, 0.0001f);
    }

    @Test
    void menuFadeIsLinearAndClampsAtOne() {
        assertEquals(0f, rounds.pauseFade(), 0.0001f);
        rounds.pauseTransition = 0.5f;
        assertEquals(0.5f, rounds.pauseFade(), 0.0001f);
        rounds.pauseTransition = 2f;
        assertEquals(1f, rounds.pauseFade(), 0.0001f);
    }

    @Test
    void pauseLifecycleResetsAndCompletesTheTransition() {
        rounds.pause();
        assertTrue(rounds.isPaused());
        assertFalse(rounds.isFullyPaused()); // winding down: the world still moves
        assertEquals(0f, rounds.pauseTransition, 0.0001f);

        rounds.advancePauseTransition(RoundManager.PAUSE_TRANSITION_TIME);
        assertTrue(rounds.isFullyPaused());
        assertEquals(1f, rounds.pauseFade(), 0.0001f);

        rounds.resume();
        assertFalse(rounds.isPaused());
        assertEquals(0f, rounds.pauseTransition, 0.0001f); // transition reset
    }

    @Test
    void resumeFadeHoldsOpaqueThenFadesAway() {
        rounds.pause();
        rounds.advancePauseTransition(RoundManager.PAUSE_TRANSITION_TIME); // fully paused
        rounds.beginResume();
        assertTrue(rounds.isResuming());
        assertEquals(1f, rounds.pauseMenuAlpha(), 0.0001f);      // t=0: opaque
        assertTrue(rounds.advanceResume(0.1f));                  // t=0.1: still holding
        assertEquals(1f, rounds.pauseMenuAlpha(), 0.0001f);
        assertTrue(rounds.advanceResume(0.1f));                  // t=0.2: end of hold
        assertEquals(1f, rounds.pauseMenuAlpha(), 0.0001f);
        assertTrue(rounds.advanceResume(0.15f));                 // t=0.35: fading
        assertEquals(1f - 0.15f / 0.3f, rounds.pauseMenuAlpha(), 0.0001f);
        assertFalse(rounds.advanceResume(0.15f));                // t=0.5: done → PLAYING
        assertFalse(rounds.isResuming());
        assertEquals(GameState.PLAYING, rounds.state);
        assertEquals(0f, rounds.pauseMenuAlpha(), 0.0001f);
    }

    @Test
    void resumeRequestedDuringWindDownResumesInstantly() {
        rounds.pause();
        rounds.advancePauseTransition(0.2f); // still slowing down, menu barely up
        rounds.beginResume();
        assertFalse(rounds.isResuming());
        assertEquals(GameState.PLAYING, rounds.state);
        assertEquals(0f, rounds.pauseTransition, 0.0001f);
    }

    @Test
    void cancelResumeSnapsBackToFullyPaused() {
        rounds.pause();
        rounds.advancePauseTransition(RoundManager.PAUSE_TRANSITION_TIME);
        rounds.beginResume();
        rounds.advanceResume(0.3f);
        assertTrue(rounds.isResuming());

        rounds.cancelResume();
        assertFalse(rounds.isResuming());
        assertEquals(GameState.PAUSED, rounds.state); // world never unfroze
        assertEquals(1f, rounds.pauseMenuAlpha(), 0.0001f); // menu back to opaque
        assertEquals(0f, rounds.resumeProgress, 0.0001f);
    }
}
