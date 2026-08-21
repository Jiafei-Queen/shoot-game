package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;

/**
 * The game's soft, muted color palette, shared by {@link GameWorld} (spawning
 * shooters and tinting impact effects) and {@link WorldRenderer} (all drawing).
 *
 * <p>The instances are shared singletons on purpose: nothing mutates them in
 * place — drawing code copies components into its own color state, and
 * {@link Particle}/{@link MuzzleFlash}/{@link Shooter} copy on construction.
 */
public final class Palette {

    private Palette() {
    }

    // world
    public static final Color bgTop = new Color(0.176f, 0.208f, 0.267f, 1f);
    public static final Color bgBottom = new Color(0.133f, 0.153f, 0.196f, 1f);
    public static final Color groundCol = new Color(0.243f, 0.290f, 0.373f, 1f);
    public static final Color groundLine = new Color(0.40f, 0.65f, 0.62f, 0.8f);

    // player
    public static final Color playerBody = new Color(0.475f, 0.843f, 0.765f, 1f);
    public static final Color playerBarrel = new Color(0.310f, 0.710f, 0.630f, 1f);
    public static final Color playerGrip = new Color(0.180f, 0.540f, 0.470f, 1f);
    public static final Color playerSlide = new Color(0.720f, 0.930f, 0.880f, 1f);
    public static final Color playerBullet = new Color(1f, 0.85f, 0.54f, 1f);
    public static final Color playerBulletCore = new Color(1f, 0.98f, 0.80f, 1f);

    // enemy
    public static final Color enemyBody = new Color(0.930f, 0.640f, 0.620f, 1f);
    public static final Color enemyBarrel = new Color(0.840f, 0.470f, 0.450f, 1f);
    public static final Color enemyGrip = new Color(0.700f, 0.330f, 0.310f, 1f);
    public static final Color enemySlide = new Color(0.980f, 0.830f, 0.810f, 1f);
    public static final Color enemyBullet = new Color(1f, 0.72f, 0.82f, 1f);
    public static final Color enemyBulletCore = new Color(1f, 0.95f, 0.98f, 1f);

    // effects
    public static final Color smokeCol = new Color(0.85f, 0.88f, 0.93f, 1f);
    public static final Color sparkCol = new Color(1f, 0.62f, 0.25f, 1f);
    public static final Color playerHit = new Color(0.55f, 0.95f, 0.85f, 1f);
    public static final Color enemyHit = new Color(1f, 0.72f, 0.68f, 1f);
    public static final Color shieldCol = new Color(0.45f, 0.78f, 0.88f, 1f);
    public static final Color shieldGlow = new Color(0.25f, 0.55f, 0.70f, 1f);

    // UI
    public static final Color healthBg = new Color(0.06f, 0.07f, 0.09f, 0.85f);
    public static final Color healthHi = new Color(0.56f, 0.88f, 0.70f, 1f);
    public static final Color healthLo = new Color(0.92f, 0.53f, 0.50f, 1f);
    public static final Color textCol = new Color(0.92f, 0.93f, 0.96f, 1f);
    public static final Color hintCol = new Color(0.78f, 0.80f, 0.86f, 0.95f);
}
