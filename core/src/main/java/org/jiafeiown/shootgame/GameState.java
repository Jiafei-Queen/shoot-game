package org.jiafeiown.shootgame;

/**
 * High-level phases of a match. The round-start transition banner is NOT a
 * state of its own on purpose: gameplay (controls, enemy AI, collisions)
 * keeps running underneath it — the banner is pure presentation timed by
 * {@link RoundManager#roundBannerTime}.
 */
enum GameState {
    PLAYING,
    GAME_OVER
}
