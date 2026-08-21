package org.jiafeiown.shootgame;

/**
 * World dimensions and layout constants. Split out of {@link GameWorld} so
 * entities ({@link Shooter}, {@link Bullet}) and the AI can clamp against the
 * world bounds without depending on the world class itself.
 */
public final class WorldConfig {

    private WorldConfig() {
    }

    public static final float WORLD_W = 800f;
    public static final float WORLD_H = 1280f;
    public static final float GROUND_TOP = 60f;
}
