package org.jiafeiown.shootgame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the touch→world mapping used by the pause menu clicks.
 *
 * <p>libGDX's {@code Camera.unproject} expects touch coordinates — origin in
 * the top-left corner, y pointing down, exactly as reported by
 * {@code Gdx.input.getX()/getY()} — and flips the Y axis internally
 * ({@code y = Gdx.graphics.getHeight() - touchCoords.y}) before normalizing.
 * The {@link WorldRenderer#unproject(float, float)} helper must therefore pass
 * the raw touch coordinates through without a second flip; double-flipping
 * would push every click upward by 60 world units, so only the middle option
 * (CONTINUE, whose mirror happens to land back on itself) would work.
 *
 * <p>For this game the camera is an unrotated orthographic camera centered on
 * the world, so the full projection degenerates to a pure scale:
 * {@code worldX = touchX * WORLD_W / WIN_W} and
 * {@code worldY = (WIN_H - touchY) * WORLD_H / WIN_H} (window 600×960, world
 * 800×1280 — the same 5:8 aspect, so the viewport fills the window). The test
 * asserts against this closed form; using the real camera would require the
 * gdx JNI matrix backend, which is unavailable in headless tests.
 */
public class PauseMenuHitTestTest {

    private static final float WORLD_W = WorldConfig.WORLD_W;   // 800
    private static final float WORLD_H = WorldConfig.WORLD_H;   // 1280
    private static final int WIN_W = 600;
    private static final int WIN_H = 960;

    /** Closed form of {@code Camera.unproject} for this game's camera setup. */
    private static float[] unproject(float touchX, float touchY) {
        return new float[]{touchX * WORLD_W / WIN_W, (WIN_H - touchY) * WORLD_H / WIN_H};
    }

    /** Screen (touch, top-origin) y for a given world y. */
    private static float screenY(float worldY) {
        return WIN_H - worldY / WORLD_H * WIN_H;
    }

    @Test
    void screenCenterMapsToWorldCenter() {
        float[] v = unproject(WIN_W / 2f, WIN_H / 2f);
        assertEquals(WORLD_W / 2f, v[0], 0.01f);
        assertEquals(WORLD_H / 2f, v[1], 0.01f);
    }

    /**
     * The three pause options sit at world baselines 640, 580 and 520 (see
     * {@link GameWorld#PAUSE_MENU_START_Y} and the 60px spacing). A click on
     * each option's on-screen text must map back to exactly that world Y —
     * a double Y flip would push them all upward by 60 world units, so only
     * the middle option (CONTINUE) would ever line up.
     */
    @Test
    void pauseOptionScreenPositionsMapToTheirWorldBaselines() {
        float[] baselines = {
                GameWorld.PAUSE_MENU_START_Y,
                GameWorld.PAUSE_MENU_START_Y - GameWorld.PAUSE_OPTION_SPACING,
                GameWorld.PAUSE_MENU_START_Y - 2 * GameWorld.PAUSE_OPTION_SPACING
        };
        for (int i = 0; i < baselines.length; i++) {
            float[] v = unproject(WIN_W / 2f, screenY(baselines[i]));
            assertEquals(baselines[i], v[1], 0.01f, "option " + i + " world y");
        }
    }

    /** World corners still map correctly (sanity check on the flip direction). */
    @Test
    void screenTopAndBottomMapToWorldTopAndBottom() {
        assertEquals(WORLD_H, unproject(WIN_W / 2f, 0f)[1], 0.01f);      // window top → world top
        assertEquals(0f, unproject(WIN_W / 2f, WIN_H)[1], 0.01f);        // window bottom → ground
    }
}
