package org.jiafeiown.shootgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Owns the whole game: entities, physics, shooting, AI and game state. Rendering lives in {@link WorldRenderer}. */
public class GameWorld {

    private static final Logger log = LogManager.getLogger(GameWorld.class);

    /** Read by {@link WorldRenderer} for the shield's fade-out. */
    static final float INVINCIBLE_TIME = 3f;
    /** Read by {@link WorldRenderer} for the round banner's timing. */
    static final float ROUND_BANNER_TIME = 2.5f;

    /** Pause menu layout, shared between the click hit-testing here and the
     *  drawing in {@link WorldRenderer}. Labels stay ASCII because the default
     *  BitmapFont has no glyphs for other scripts. */
    static final String[] PAUSE_OPTIONS = {"CONTINUE", "END GAME", "RESTART ROUND"};
    static final float PAUSE_OPTION_SCALE = 2f;
    static final float PAUSE_OPTION_SPACING = 60f;
    /** Y of the first (top) option's baseline; the rest sit below it. */
    static final float PAUSE_MENU_START_Y = WorldConfig.WORLD_H * 0.5f;

    /** Top-right pause button (mobile/web; also clickable on desktop). A tap
     *  on it does exactly what ESC does: {@link RoundManager#pause()} with the
     *  slow-mo wind-down and the pause menu. World-space rect, shared between
     *  the hit-testing here and the drawing in {@link WorldRenderer}. */
    static final float PAUSE_BTN_SIZE = 56f;
    static final float PAUSE_BTN_MARGIN = 20f;
    static final float PAUSE_BTN_X = WorldConfig.WORLD_W - PAUSE_BTN_SIZE - PAUSE_BTN_MARGIN;
    static final float PAUSE_BTN_Y = WorldConfig.WORLD_H - PAUSE_BTN_SIZE - PAUSE_BTN_MARGIN;

    private WorldRenderer renderer;
    private final EnemyAI enemyAI = new EnemyAI(this);
    private final CollisionSystem collision = new CollisionSystem(this);
    /** Round progression and match statistics; read by the renderer and collision system. */
    final RoundManager rounds = new RoundManager(this);
    /** Visual effects (particles, muzzle flashes); read by the renderer. */
    final FxSystem fx = new FxSystem();
    /** Sound effects and the looping pause music. Null-safe: unit tests build
     *  a GameWorld without loading audio, in which case every playback call
     *  is a silent no-op. */
    final AudioManager audio = new AudioManager();

    Shooter player;
    final Array<Shooter> enemies = new Array<>();
    final Array<Bullet> bullets = new Array<>();

    float time;

    public void create() {
        renderer = new WorldRenderer(this);
        renderer.create();
        audio.create();
        log.info("GameWorld created (world {}x{}, ground top {})",
                WorldConfig.WORLD_W, WorldConfig.WORLD_H, WorldConfig.GROUND_TOP);
        reset();
    }

    public void reset() {
        log.info("Game reset");
        player = new Shooter(true, WorldConfig.WORLD_W * 0.25f, WorldConfig.GROUND_TOP + 80f, Shooter.BASE_HP, 0.14f,
                Palette.playerBody, Palette.playerBarrel, Palette.playerGrip, Palette.playerSlide,
                Palette.playerBullet, Palette.playerBulletCore);
        player.spin = 2.2f;
        time = 0f;
        bullets.clear();
        fx.clear();
        rounds.reset();
    }

    public void render() {
        float dt = MathUtils.clamp(Gdx.graphics.getDeltaTime(), 0f, 1f / 30f);
        // audio fades must keep moving even while the game is frozen on the
        // pause screen, so drive them here rather than inside update()
        audio.update(dt);
        update(dt);
        handleInput();
        renderer.render();
    }

    private void update(float dt) {
        if (rounds.isGameOver()) return;
        if (rounds.isPaused()) {
            if (rounds.isResuming()) {
                // menu is fading out: keep the world frozen until it's gone,
                // then the state flips back to PLAYING
                rounds.advanceResume(dt);
                return;
            }
            // ESC doesn't freeze the world dead: it eases through a slow-mo
            // curve (1x → 0.2x → 0x) while the pause menu fades in, so the
            // action visibly winds down instead of cutting out. The transition
            // itself advances in real time; only the world's dt is scaled.
            float ts = rounds.advancePauseTransition(dt);
            if (ts <= 0f) return; // fully frozen
            dt *= ts;
        }

        time += dt;
        rounds.updateBanner(dt);
        float invBefore = player.invincibleTime;
        player.update(dt);
        if (invBefore > 0f && player.invincibleTime <= 0f) {
            // the shield pops out with a spark burst when the grace period ends
            fx.burst(player.x, player.y, Palette.shieldCol, 14, 240f);
            log.debug("Invincibility shield expired");
        }
        for (Shooter e : enemies) e.update(dt);
        enemyAI.update(dt);

        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(dt);
            if (b.dead) bullets.removeIndex(i);
        }
        fx.update(dt);

        collision.update();
        rounds.update();
    }

    private void handleInput() {
        // while paused the only inputs that matter are the pause menu's
        if (rounds.isPaused()) {
            handlePauseInput();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset();
        if (rounds.isGameOver()) {
            // touch devices have no R key; a tap anywhere restarts (a mouse
            // click works too, matching the pause menu's click interactions)
            if (Gdx.input.justTouched()) {
                log.info("Restarting via tap after game over");
                reset();
            }
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            rounds.pause();
            log.info("Game paused (round {})", rounds.round);
            return;
        }
        if (Gdx.input.justTouched() && pauseButtonHit(0)) {
            rounds.pause();
            log.info("Game paused via pause button (round {})", rounds.round);
            return;
        }
        if (fireHeld() && player.fireCooldown <= 0f) {
            fire(player);
        }
    }

    /** True while the player wants to fire: SPACE held (desktop) or any touch
     *  down outside the pause button (mobile/web, and mouse-hold on desktop).
     *  A quick tap fires one shot; holding keeps firing on cooldown, exactly
     *  like holding SPACE. */
    private boolean fireHeld() {
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) return true;
        for (int i = 0; i < 10; i++) {
            if (Gdx.input.isTouched(i) && !pauseButtonHit(i)) return true;
        }
        return false;
    }

    /** True if pointer {@code pointer}'s current screen position lies on the
     *  top-right pause button. Touches that start there must pause, never fire. */
    private boolean pauseButtonHit(int pointer) {
        float[] p = renderer.unproject(Gdx.input.getX(pointer), Gdx.input.getY(pointer));
        return renderer.pauseButtonAt(p[0], p[1]);
    }

    /** Pause menu interactions: ESC resumes (or cancels a resume in progress),
     *  clicking an option acts on it. */
    private void handlePauseInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (rounds.isResuming()) {
                rounds.cancelResume();
                log.info("Resume cancelled");
            } else {
                rounds.beginResume();
                log.info("Game resuming");
            }
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && !Gdx.input.justTouched()) return;
        float[] p = renderer.unproject(Gdx.input.getX(), Gdx.input.getY());
        switch (renderer.pauseOptionAt(p[0], p[1])) {
            case 0: // CONTINUE
                rounds.beginResume();
                log.info("Game resuming");
                break;
            case 1: // END GAME
                rounds.endGame();
                break;
            case 2: // RESTART ROUND
                rounds.restartRound();
                break;
            default:
                break;
        }
    }

    /**
     * Fires a shooter's weapon: spawns the bullet plus the muzzle flash and
     * smoke burst, and plays the matching shot sound. Shared by the player
     * input and {@link EnemyAI}.
     */
    void fire(Shooter s) {
        s.shoot(this);
        fx.muzzleEffects(s);
        if (s.isPlayer) audio.playPlayerShoot();
        else audio.playEnemyShoot();
    }

    public void spawnBullet(Shooter owner, float x, float y, float angle) {
        if (player.dead) return;
        bullets.add(new Bullet(owner, x, y, angle));
    }

    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    /** True on touch-first platforms (Android/iOS/Web). Desktop keeps the
     *  keyboard as the primary input; the shared code uses this to pick
     *  platform-appropriate hints and input behaviour. */
    static boolean isTouchDevice() {
        switch (Gdx.app.getType()) {
            case Android:
            case iOS:
            case WebGL:
                return true;
            default:
                return false;
        }
    }

    public void dispose() {
        renderer.dispose();
        audio.dispose();
    }
}
