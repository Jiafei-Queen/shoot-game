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

    private WorldRenderer renderer;
    private final EnemyAI enemyAI = new EnemyAI(this);
    private final CollisionSystem collision = new CollisionSystem(this);
    /** Round progression and match statistics; read by the renderer and collision system. */
    final RoundManager rounds = new RoundManager(this);
    /** Visual effects (particles, muzzle flashes); read by the renderer. */
    final FxSystem fx = new FxSystem();

    Shooter player;
    final Array<Shooter> enemies = new Array<>();
    final Array<Bullet> bullets = new Array<>();

    float time;

    public void create() {
        renderer = new WorldRenderer(this);
        renderer.create();
        log.info("GameWorld created (world {}x{}, ground top {})",
                WorldConfig.WORLD_W, WorldConfig.WORLD_H, WorldConfig.GROUND_TOP);
        reset();
    }

    public void reset() {
        log.info("Game reset");
        player = new Shooter(true, WorldConfig.WORLD_W * 0.25f, WorldConfig.GROUND_TOP + 80f, Shooter.BASE_HP, 0.28f,
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
        update(dt);
        handleInput();
        renderer.render();
    }

    private void update(float dt) {
        if (rounds.state == GameState.PAUSED) return;
        if (rounds.isGameOver()) return;

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
        if (rounds.isGameOver()) return;
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            rounds.pause();
            log.info("Game paused (round {})", rounds.round);
            return;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && player.fireCooldown <= 0f) {
            fire(player);
        }
    }

    /** Pause menu interactions: ESC resumes, clicking an option acts on it. */
    private void handlePauseInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            rounds.resume();
            log.info("Game resumed");
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) return;
        float[] p = renderer.unproject(Gdx.input.getX(), Gdx.input.getY());
        switch (renderer.pauseOptionAt(p[0], p[1])) {
            case 0: // CONTINUE
                rounds.resume();
                log.info("Game resumed");
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
     * smoke burst. Shared by the player input and {@link EnemyAI}.
     */
    void fire(Shooter s) {
        s.shoot(this);
        fx.muzzleEffects(s);
    }

    public void spawnBullet(Shooter owner, float x, float y, float angle) {
        if (player.dead) return;
        bullets.add(new Bullet(owner, x, y, angle));
    }

    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    public void dispose() {
        renderer.dispose();
    }
}
