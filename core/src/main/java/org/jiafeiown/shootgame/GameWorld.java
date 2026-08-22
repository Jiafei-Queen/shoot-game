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
    static final String[] PAUSE_OPTIONS = {"CONTINUE", "END GAME", "RESTART ROUND", "MAIN MENU"};
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

    /** Start screen layout, shared between the click hit-testing here and the
     *  drawing in {@link WorldRenderer}. The title sits in the upper-middle
     *  and the START button in the lower-middle of the screen. */
    static final float START_TITLE_Y = WorldConfig.WORLD_H * 0.62f;
    static final float START_BTN_W = 300f;
    static final float START_BTN_H = 76f;
    static final float START_BTN_CX = WorldConfig.WORLD_W * 0.5f;
    static final float START_BTN_CY = WorldConfig.WORLD_H * 0.35f;

    private WorldRenderer renderer;
    private final EnemyAI enemyAI = new EnemyAI(this);
    /** Bullet/entity collision; package-private so tests can drive a real frame. */
    final CollisionSystem collision = new CollisionSystem(this);
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

    /** Set when play begins via a fire-shaped input (the ENTER/SPACE press or
     *  tap on the start screen, a tap-to-restart after game over). That same
     *  key/tap is still down on the following frames, so without this latch
     *  entering the match would also fire a shot. Firing stays suppressed
     *  until every fire input is released once, then behaves normally.
     *  Package-private so tests can inspect the latch without Gdx.input. */
    boolean fireLatch;

    public void create() {
        renderer = new WorldRenderer(this);
        renderer.create();
        audio.create();
        log.info("GameWorld created (world {}x{}, ground top {})",
                WorldConfig.WORLD_W, WorldConfig.WORLD_H, WorldConfig.GROUND_TOP);
        toMenu();
    }

    /** Builds a fresh player standing on the ground, ready for a new match. */
    private void spawnPlayer() {
        player = new Shooter(true, WorldConfig.WORLD_W * 0.25f, WorldConfig.GROUND_TOP + 80f, Shooter.BASE_HP, 0.14f,
                Palette.playerBody, Palette.playerBarrel, Palette.playerGrip, Palette.playerSlide,
                Palette.playerBullet, Palette.playerBulletCore);
        player.spin = 2.2f;
    }

    public void reset() {
        log.info("Game reset");
        spawnPlayer();
        time = 0f;
        bullets.clear();
        fx.clear();
        rounds.reset();
    }

    /** Opens the start screen: the menu is drawn over the empty field, with a
     *  fresh player standing on it. Nothing moves while the menu is up. */
    void toMenu() {
        log.info("Showing start screen");
        spawnPlayer();
        time = 0f;
        bullets.clear();
        fx.clear();
        enemies.clear();
        rounds.toMenu();
    }

    /** Launches a new match from the start screen: fresh round 1 with every
     *  stat reset. Round 1 opens with the round-start jingle, played here —
     *  inside the START click's user gesture. That matters on the web build:
     *  browsers keep the Web Audio context suspended until audio is played in
     *  a gesture, and playing the jingle as a {@link Music} (an HTML5 media
     *  element) on this exact click is what engages the audio pipeline, so
     *  every sound afterwards is audible without an extra interaction. */
    void startGame() {
        log.info("Starting new game from start screen");
        reset();
        // the START key/tap is still down: don't let it fire the first shot
        fireLatch = true;
        audio.playNextRound();
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
        if (rounds.isMainMenu()) return;
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
        // the start screen has its own input handling
        if (rounds.isMainMenu()) {
            handleMenuInput();
            return;
        }
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
                // the restarting tap is still down: don't let it fire either
                fireLatch = true;
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
     *  like holding SPACE.
     *
     *  While {@link #fireLatch} is set, firing is suppressed until every fire
     *  input has been released once — the key or tap that started the match
     *  must never carry over into an accidental first shot. */
    private boolean fireHeld() {
        return fireHeld(rawFireInput());
    }

    /** Pure decision path of {@link #fireHeld()}, split out so the latch is
     *  unit-testable without Gdx.input: while {@link #fireLatch} is set the
     *  held input is ignored, and the first observation of "nothing held"
     *  clears the latch so normal firing resumes. */
    boolean fireHeld(boolean rawInput) {
        if (fireLatch) {
            if (rawInput) return false; // still holding the starting key/tap
            fireLatch = false; // released: normal firing resumes from here
        }
        return rawInput;
    }

    private boolean rawFireInput() {
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

    /** Start screen interactions: ENTER/SPACE (desktop) or a tap on the START
     *  button starts the game; on touch devices any tap starts it, since the
     *  menu has no other targets. */
    private void handleMenuInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            startGame();
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && !Gdx.input.justTouched()) return;
        float[] p = renderer.unproject(Gdx.input.getX(), Gdx.input.getY());
        if (isTouchDevice() || renderer.startButtonAt(p[0], p[1])) {
            startGame();
        }
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
            case 3: // MAIN MENU
                toMenu();
                log.info("Returning to start screen");
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
