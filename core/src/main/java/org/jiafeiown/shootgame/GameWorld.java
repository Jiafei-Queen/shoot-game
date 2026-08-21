package org.jiafeiown.shootgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Owns the whole game: entities, physics, shooting, AI and game state. Rendering lives in {@link WorldRenderer}. */
public class GameWorld {

    private static final Logger log = LogManager.getLogger(GameWorld.class);

    public static final float WORLD_W = 800f;
    public static final float WORLD_H = 1280f;
    public static final float GROUND_TOP = 60f;

    private static final int MAX_ENEMIES = 6;
    private static final float ENEMY_GROWTH = 1.05f;
    /** Read by {@link WorldRenderer} for the shield's fade-out. */
    static final float INVINCIBLE_TIME = 3f;
    /** Read by {@link WorldRenderer} for the round banner's timing. */
    static final float ROUND_BANNER_TIME = 2.5f;

    private WorldRenderer renderer;
    private final EnemyAI enemyAI = new EnemyAI(this);
    private final CollisionSystem collision = new CollisionSystem(this);
    /** Round progression and match statistics; read by the renderer and collision system. */
    final RoundManager rounds = new RoundManager(this);

    Shooter player;
    final Array<Shooter> enemies = new Array<>();
    final Array<Bullet> bullets = new Array<>();
    final Array<Particle> particles = new Array<>();
    final Array<MuzzleFlash> flashes = new Array<>();

    float time;

    public void create() {
        renderer = new WorldRenderer(this);
        renderer.create();
        log.info("GameWorld created (world {}x{}, ground top {})", WORLD_W, WORLD_H, GROUND_TOP);
        reset();
    }

    public void reset() {
        log.info("Game reset");
        player = new Shooter(true, WORLD_W * 0.25f, GROUND_TOP + 80f, Shooter.BASE_HP, 0.28f,
                Palette.playerBody, Palette.playerBarrel, Palette.playerGrip, Palette.playerSlide,
                Palette.playerBullet, Palette.playerBulletCore);
        player.spin = 2.2f;
        time = 0f;
        bullets.clear();
        particles.clear();
        flashes.clear();
        rounds.reset();
    }

    public void render() {
        float dt = MathUtils.clamp(Gdx.graphics.getDeltaTime(), 0f, 1f / 30f);
        update(dt);
        handleInput();
        renderer.render();
    }

    private void update(float dt) {
        if (rounds.isGameOver()) return;
        time += dt;
        rounds.updateBanner(dt);
        float invBefore = player.invincibleTime;
        player.update(dt);
        if (invBefore > 0f && player.invincibleTime <= 0f) {
            // the shield pops out with a spark burst when the grace period ends
            burst(player.x, player.y, Palette.shieldCol, 14, 240f);
            log.debug("Invincibility shield expired");
        }
        for (Shooter e : enemies) e.update(dt);
        enemyAI.update(dt);

        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(dt);
            if (b.dead) bullets.removeIndex(i);
        }
        for (int i = particles.size - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update(dt);
            if (p.life <= 0f) particles.removeIndex(i);
        }
        for (int i = flashes.size - 1; i >= 0; i--) {
            MuzzleFlash f = flashes.get(i);
            f.life -= dt;
            if (f.life <= 0f) flashes.removeIndex(i);
        }

        collision.update();
        rounds.update();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset();
        if (rounds.isGameOver()) return;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && player.fireCooldown <= 0f) {
            fire(player);
        }
    }

    /**
     * Fires a shooter's weapon: spawns the bullet plus the muzzle flash and
     * smoke burst. Shared by the player input and {@link EnemyAI}.
     */
    void fire(Shooter s) {
        s.shoot(this);
        addFlash(s);
        burst(muzzleX(s), muzzleY(s), Palette.smokeCol, 6, 110f);
    }

    public void spawnBullet(Shooter owner, float x, float y, float angle) {
        if (player.dead) return;
        bullets.add(new Bullet(owner, x, y, angle));
    }

    private void addFlash(Shooter s) {
        flashes.add(new MuzzleFlash(muzzleX(s), muzzleY(s), s.angle, s.bullet));
        // hot sparks sprayed forward from the muzzle
        for (int i = 0; i < 5; i++) {
            float a = s.angle + MathUtils.random(-0.6f, 0.6f);
            particles.add(new Particle(muzzleX(s), muzzleY(s),
                    a, MathUtils.random(140f, 340f),
                    MathUtils.random(0.10f, 0.22f),
                    MathUtils.random(1.2f, 2.2f),
                    Palette.sparkCol));
        }
    }

    private float muzzleX(Shooter s) {
        return s.x + MathUtils.cos(s.angle) * Shooter.MUZZLE_OFFSET;
    }

    private float muzzleY(Shooter s) {
        return s.y + MathUtils.sin(s.angle) * Shooter.MUZZLE_OFFSET;
    }

    /** Spawns a radial burst of particles at (x, y). Also used by {@link CollisionSystem} for impact effects. */
    void burst(float x, float y, Color c, int n, float speed) {
        for (int i = 0; i < n; i++) {
            particles.add(new Particle(x, y,
                    MathUtils.random() * MathUtils.PI2,
                    MathUtils.random(30f, speed),
                    MathUtils.random(0.22f, 0.45f),
                    MathUtils.random(1.5f, 3.2f), c));
        }
    }

    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    public void dispose() {
        renderer.dispose();
    }
}
