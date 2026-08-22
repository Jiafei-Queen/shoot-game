package org.jiafeiown.shootgame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the start-screen flow: a fresh game opens on the menu over an
 * empty field, START launches a fresh round 1, and the pause menu's MAIN MENU
 * option returns to the start screen, abandoning the match.
 *
 * <p>{@link GameWorld} is instantiable headlessly — rendering objects are only
 * created in {@code create()}, which this test never calls.
 */
public class StartMenuTest {

    @Test
    void freshWorldOpensOnTheStartScreen() {
        GameWorld world = new GameWorld();
        world.toMenu();
        assertEquals(GameState.MAIN_MENU, world.rounds.state);
        assertTrue(world.rounds.isMainMenu());
        // the field behind the menu is empty: no enemies, bullets or effects
        assertEquals(0, world.enemies.size);
        assertEquals(0, world.bullets.size);
        assertEquals(0, world.fx.particles.size);
        assertFalse(world.player.dead);
    }

    @Test
    void startGameLaunchesFreshRoundOne() {
        GameWorld world = new GameWorld();
        world.toMenu();
        world.startGame();
        assertEquals(GameState.PLAYING, world.rounds.state);
        assertFalse(world.rounds.isMainMenu());
        assertEquals(1, world.rounds.round);
        assertEquals(0, world.rounds.kills);
        assertTrue(world.enemies.size > 0);
        assertFalse(world.player.dead);
    }

    @Test
    void pauseMenuMainMenuOptionReturnsToStartScreen() {
        GameWorld world = new GameWorld();
        world.toMenu();
        world.startGame();
        assertTrue(world.enemies.size > 0);

        world.rounds.pause();
        world.toMenu();
        assertEquals(GameState.MAIN_MENU, world.rounds.state);
        assertFalse(world.rounds.isPaused());
        // the abandoned match is left behind: field emptied again
        assertEquals(0, world.enemies.size);
        assertEquals(0, world.bullets.size);
    }

    @Test
    void startScreenLayoutPutsTitleAboveButton() {
        // title sits in the upper-middle, the button in the lower-middle,
        // both vertically centered on screen
        assertTrue(GameWorld.START_TITLE_Y > WorldConfig.WORLD_H * 0.5f);
        assertTrue(GameWorld.START_BTN_CY < WorldConfig.WORLD_H * 0.5f);
        assertEquals(WorldConfig.WORLD_W * 0.5f, GameWorld.START_BTN_CX, 0.001f);
    }
}
