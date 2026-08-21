package org.jiafeiown.shootgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Owns the whole game: entities, physics, shooting, AI and rendering. */
public class GameWorld {

    private static final Logger log = LogManager.getLogger(GameWorld.class);

    public static final float WORLD_W = 800f;
    public static final float WORLD_H = 1280f;
    public static final float GROUND_TOP = 60f;

    private static final float ENEMY_TOLERANCE = 0.10f;
    private static final float DODGE_HORIZON = 0.5f;
    private static final float DODGE_COOLDOWN = 0.6f;
    private static final float BULLET_COLLIDE_RADIUS = 12f;
    private static final float BULLET_HIT_RADIUS = 4f;
    private static final int MAX_ENEMIES = 6;
    private static final float ENEMY_GROWTH = 1.05f;
    private static final float INVINCIBLE_TIME = 3f;
    private static final float ROUND_BANNER_TIME = 2.5f;

    private OrthographicCamera cam;
    private FitViewport viewport;
    private ShapeRenderer shape;
    private SpriteBatch batch;
    private BitmapFont font;

    private Shooter player;
    private final Array<Shooter> enemies = new Array<>();
    private final Array<Bullet> bullets = new Array<>();
    private final Array<Particle> particles = new Array<>();
    private final Array<MuzzleFlash> flashes = new Array<>();

    private int round = 1;
    private int kills = 0;
    private int damageDealt = 0;
    private boolean gameOver;
    private float time;
    /** Counts down while the round-start transition banner is on screen. */
    private float roundBannerTime = 0f;
    /** Enemy count of the current round, shown under the banner title. */
    private int roundEnemies = 0;

    // soft, muted palette
    private final Color bgTop = new Color(0.176f, 0.208f, 0.267f, 1f);
    private final Color bgBottom = new Color(0.133f, 0.153f, 0.196f, 1f);
    private final Color groundCol = new Color(0.243f, 0.290f, 0.373f, 1f);
    private final Color groundLine = new Color(0.40f, 0.65f, 0.62f, 0.8f);
    private final Color playerBody = new Color(0.475f, 0.843f, 0.765f, 1f);
    private final Color playerBarrel = new Color(0.310f, 0.710f, 0.630f, 1f);
    private final Color playerGrip = new Color(0.180f, 0.540f, 0.470f, 1f);
    private final Color playerSlide = new Color(0.720f, 0.930f, 0.880f, 1f);
    private final Color playerBullet = new Color(1f, 0.85f, 0.54f, 1f);
    private final Color playerBulletCore = new Color(1f, 0.98f, 0.80f, 1f);
    private final Color enemyBody = new Color(0.930f, 0.640f, 0.620f, 1f);
    private final Color enemyBarrel = new Color(0.840f, 0.470f, 0.450f, 1f);
    private final Color enemyGrip = new Color(0.700f, 0.330f, 0.310f, 1f);
    private final Color enemySlide = new Color(0.980f, 0.830f, 0.810f, 1f);
    private final Color enemyBullet = new Color(1f, 0.72f, 0.82f, 1f);
    private final Color enemyBulletCore = new Color(1f, 0.95f, 0.98f, 1f);
    private final Color smokeCol = new Color(0.85f, 0.88f, 0.93f, 1f);
    private final Color playerHit = new Color(0.55f, 0.95f, 0.85f, 1f);
    private final Color enemyHit = new Color(1f, 0.72f, 0.68f, 1f);
    private final Color shieldCol = new Color(0.45f, 0.78f, 0.88f, 1f);
    private final Color shieldGlow = new Color(0.25f, 0.55f, 0.70f, 1f);
    private final Color healthBg = new Color(0.06f, 0.07f, 0.09f, 0.85f);
    private final Color healthHi = new Color(0.56f, 0.88f, 0.70f, 1f);
    private final Color healthLo = new Color(0.92f, 0.53f, 0.50f, 1f);
    private final Color textCol = new Color(0.92f, 0.93f, 0.96f, 1f);
    private final Color hintCol = new Color(0.78f, 0.80f, 0.86f, 0.95f);

    private final Matrix4 shooterM = new Matrix4();
    private final Matrix4 idM = new Matrix4();
    private final Color lerpTmp = new Color();
    private final GlyphLayout layout = new GlyphLayout();

    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(false, WORLD_W, WORLD_H);
        viewport = new FitViewport(WORLD_W, WORLD_H, cam);
        shape = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        log.info("GameWorld created (world {}x{}, ground top {})", WORLD_W, WORLD_H, GROUND_TOP);
        reset();
    }

    public void reset() {
        log.info("Game reset");
        player = new Shooter(true, WORLD_W * 0.25f, GROUND_TOP + 80f, Shooter.BASE_HP, 0.28f,
                playerBody, playerBarrel, playerGrip, playerSlide, playerBullet, playerBulletCore);
        player.spin = 2.2f;
        round = 1;
        kills = 0;
        damageDealt = 0;
        gameOver = false;
        time = 0f;
        bullets.clear();
        particles.clear();
        flashes.clear();
        spawnRound();
    }

    /** Spawns this round's enemies: one more than the previous round (up to
     *  {@link #MAX_ENEMIES}), each with +5% max hp and bullet damage per round. */
    private void spawnRound() {
        enemies.clear();
        int count = Math.min(round, MAX_ENEMIES);
        float mult = (float) Math.pow(ENEMY_GROWTH, round - 1);
        int hp = Math.max(1, Math.round(Shooter.BASE_HP * mult));
        int dmg = Math.max(1, Math.round(Shooter.BULLET_DAMAGE * mult));
        for (int i = 0; i < count; i++) {
            float ex = WORLD_W * (0.22f + 0.56f * i / Math.max(1f, count - 1f));
            Shooter e = new Shooter(false, ex, GROUND_TOP + 80f, hp, 1.15f,
                    enemyBody, enemyBarrel, enemyGrip, enemySlide, enemyBullet, enemyBulletCore);
            e.spin = 1.6f;
            e.damage = dmg;
            enemies.add(e);
        }
        // the player is briefly invincible at the top of every round
        player.invincibleTime = INVINCIBLE_TIME;
        roundEnemies = count;
        roundBannerTime = ROUND_BANNER_TIME;
        log.info("Round {} started: {} enemies (hp={}, dmg={})", round, count, hp, dmg);
    }

    public void render() {
        float dt = MathUtils.clamp(Gdx.graphics.getDeltaTime(), 0f, 1f / 30f);
        update(dt);
        handleInput();
        viewport.apply();
        ScreenUtils.clear(bgBottom.r, bgBottom.g, bgBottom.b, 1f);
        shape.setProjectionMatrix(cam.combined);
        // Blending must be (re-)enabled every frame: on some backends the state
        // set once in create() is lost before the first frame, which would make
        // every translucent shape (shadows, flashes, the shield) render opaque.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setTransformMatrix(idM.idt());
        drawBackground();
        drawGround();
        if (!player.dead) drawShadow(player);
        for (Shooter e : enemies) if (!e.dead) drawShadow(e);
        for (Bullet b : bullets) drawBullet(b);
        for (MuzzleFlash f : flashes) drawFlash(f);
        drawShooterFilled(player);
        for (Shooter e : enemies) drawShooterFilled(e);
        drawShieldFilled();
        for (Particle p : particles) drawParticle(p);
        if (!player.dead) drawHealthBar(player);
        for (Shooter e : enemies) if (!e.dead) drawHealthBar(e);
        if (gameOver) {
            shape.setColor(0f, 0f, 0f, 0.55f);
            shape.rect(0f, 0f, WORLD_W, WORLD_H);
        }
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setTransformMatrix(idM.idt());
        if (!gameOver) {
            drawShooterOutline(player);
            for (Shooter e : enemies) drawShooterOutline(e);
            drawShieldRing();
            if (!player.dead) drawHealthBarBorder(player);
            for (Shooter e : enemies) if (!e.dead) drawHealthBarBorder(e);
        }
        shape.end();

        drawHud();
        drawRoundBanner();
    }

    private void update(float dt) {
        if (gameOver) return;
        time += dt;
        if (roundBannerTime > 0f) roundBannerTime = Math.max(0f, roundBannerTime - dt);
        float invBefore = player.invincibleTime;
        player.update(dt);
        if (invBefore > 0f && player.invincibleTime <= 0f) {
            // the shield pops out with a spark burst when the grace period ends
            burst(player.x, player.y, shieldCol, 14, 240f);
            log.debug("Invincibility shield expired");
        }
        for (Shooter e : enemies) e.update(dt);
        updateEnemyAI(dt);

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

        checkBulletHits();
        checkBulletCollisions();
        resolveShooterCollisions();

        // player died: freeze the world and show the settlement screen
        if (player.dead) {
            log.info("Game over: player defeated in round {} | kills={}, damageDealt={}", round, kills, damageDealt);
            gameOver = true;
            return;
        }
        // all enemies of the round cleared: heal the player 30% and move on
        boolean allDead = true;
        for (Shooter e : enemies) {
            if (!e.dead) {
                allDead = false;
                break;
            }
        }
        if (allDead) {
            int healed = Math.round(player.maxHp * 0.30f);
            round++;
            player.hp = Math.min(player.maxHp, player.hp + healed);
            log.info("Round {} cleared | healed +{} HP ({} → {}) | starting round {}",
                    round - 1, healed, player.hp - healed, player.hp, round);
            spawnRound();
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset();
        if (gameOver) return;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && player.fireCooldown <= 0f) {
            player.shoot(this);
            addFlash(player);
            burst(muzzleX(player), muzzleY(player), smokeCol, 6, 110f);
        }
    }

    private void updateEnemyAI(float dt) {
        if (player.dead) return;
        for (Shooter enemy : enemies) {
            if (!enemy.dead) updateEnemyAI(enemy, dt);
        }
    }

    private void updateEnemyAI(Shooter enemy, float dt) {
        if (enemy.dodgeCooldown > 0f) enemy.dodgeCooldown -= dt;

        // a player bullet is about to hit: fire in a direction whose recoil
        // shoves the enemy off the bullet's path (the shot also heads back
        // toward the player, so the dodge doubles as a counter-attack)
        if (enemy.fireCooldown <= 0f && enemy.dodgeCooldown <= 0f) {
            Bullet threat = findIncomingThreat(enemy);
            if (threat != null) {
                enemy.angle = dodgeAimAngle(enemy, threat);
                enemy.shoot(this);
                addFlash(enemy);
                burst(muzzleX(enemy), muzzleY(enemy), smokeCol, 6, 110f);
                enemy.dodgeCooldown = DODGE_COOLDOWN;
                return;
            }
        }

        // resting on the ground: the muzzle doesn't spin, so it can't align
        // to aim; fire every so often just to hop back into the air
        if (enemy.grounded) {
            if (enemy.fireCooldown <= 0f) {
                enemy.shoot(this);
                addFlash(enemy);
                burst(muzzleX(enemy), muzzleY(enemy), smokeCol, 6, 110f);
                enemy.fireCooldown = MathUtils.random(0.7f, 1.1f);
            }
            return;
        }

        float dx = player.x - enemy.x;
        float dy = player.y - enemy.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // lead the target: predict where the player will be when the bullet
        // arrives, accounting for gravity on the player and the world bounds
        float flight = dist / Shooter.BULLET_SPEED;
        float px = player.x + player.vx * flight;
        float py = player.y + player.vy * flight
                - 0.5f * Shooter.GRAVITY * flight * flight;
        px = MathUtils.clamp(px, 20f, GameWorld.WORLD_W - 20f);
        py = MathUtils.clamp(py, GameWorld.GROUND_TOP + 18f, GameWorld.WORLD_H - 18f);
        float desired = MathUtils.atan2(py - enemy.y, px - enemy.x);

        // drift back toward the player when too far away
        if (dist > 460f) desired += MathUtils.clamp((dist - 460f) / 800f, 0f, 0.5f) * MathUtils.PI;

        // the enemy muzzle spins like the player's; fire only when it sweeps
        // across the predicted aim, in short bursts
        float diff = desired - enemy.angle;
        while (diff > MathUtils.PI) diff -= MathUtils.PI2;
        while (diff < -MathUtils.PI) diff += MathUtils.PI2;

        if (enemy.fireCooldown <= 0f && Math.abs(diff) < ENEMY_TOLERANCE) {
            enemy.shoot(this);
            addFlash(enemy);
            burst(muzzleX(enemy), muzzleY(enemy), smokeCol, 6, 110f);
            if (enemy.burst <= 0) enemy.burst = 3;
            enemy.burst--;
            enemy.fireCooldown = enemy.burst > 0 ? 0.15f : MathUtils.random(0.9f, 1.5f);
        }
    }

    /** Nearest player bullet that will hit the given enemy within the dodge horizon, or null. */
    private Bullet findIncomingThreat(Shooter enemy) {
        Bullet best = null;
        float bestT = Float.MAX_VALUE;
        for (int i = 0; i < bullets.size; i++) {
            Bullet b = bullets.get(i);
            if (b.dead || !b.owner.isPlayer) continue;
            float t = bulletTimeToEnemy(b, enemy);
            if (t >= 0f && t < DODGE_HORIZON && t < bestT) {
                best = b;
                bestT = t;
            }
        }
        return best;
    }

    /** Time until a bullet enters the enemy's hitbox, or -1 if it never does. */
    private float bulletTimeToEnemy(Bullet b, Shooter enemy) {
        float cos = MathUtils.cos(enemy.angle);
        float sin = MathUtils.sin(enemy.angle);
        float dx = b.x - enemy.x, dy = b.y - enemy.y;
        float lx = cos * dx + sin * dy;
        float ly = -sin * dx + cos * dy;
        float lvx = cos * b.vx + sin * b.vy;
        float lvy = -sin * b.vx + cos * b.vy;
        float best = -1f;
        for (float[] hb : Shooter.HITBOXES) {
            float t = hitTimeAlong(lx, ly, lvx, lvy,
                    hb[0] - BULLET_HIT_RADIUS, hb[1] - BULLET_HIT_RADIUS,
                    hb[2] + BULLET_HIT_RADIUS, hb[3] + BULLET_HIT_RADIUS);
            if (t >= 0f && (best < 0f || t < best)) best = t;
        }
        return best;
    }

    /** Time until a ray (px,py)+(vx,vy)*t enters the box, or -1 if it never does. */
    private static float hitTimeAlong(float px, float py, float vx, float vy,
                                      float minX, float minY, float maxX, float maxY) {
        float tmin = -Float.MAX_VALUE, tmax = Float.MAX_VALUE;
        if (Math.abs(vx) < 0.0001f) {
            if (px < minX || px > maxX) return -1f;
        } else {
            float t1 = (minX - px) / vx;
            float t2 = (maxX - px) / vx;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(vy) < 0.0001f) {
            if (py < minY || py > maxY) return -1f;
        } else {
            float t1 = (minY - py) / vy;
            float t2 = (maxY - py) / vy;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (tmax < 0f || tmin > tmax) return -1f;
        return Math.max(0f, tmin);
    }

    /**
     * Direction to fire so that the recoil shoves the enemy perpendicular to
     * the incoming bullet's path, away from it.
     */
    private float dodgeAimAngle(Shooter enemy, Bullet b) {
        float pushX = -b.vy, pushY = b.vx;
        float len = (float) Math.sqrt(pushX * pushX + pushY * pushY);
        if (len < 0.0001f) return 0f;
        pushX /= len;
        pushY /= len;
        float ex = enemy.x - b.x, ey = enemy.y - b.y;
        if (pushX * ex + pushY * ey < 0f) {
            pushX = -pushX;
            pushY = -pushY;
        }
        return MathUtils.atan2(-pushY, -pushX);
    }

    private void checkBulletHits() {
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            if (b.dead) {
                bullets.removeIndex(i);
                continue;
            }
            if (!player.dead && player.bulletHits(b.x, b.y, BULLET_HIT_RADIUS)) {
                b.dead = true;
                if (player.invincibleTime > 0f) {
                    // round-start shield absorbs the bullet
                    burst(b.x, b.y, shieldCol, 8, 170f);
                    log.debug("Bullet absorbed by invincibility shield");
                } else {
                    int hpBefore = player.hp;
                    player.takeDamage(b.owner.damage, b.nx, b.ny);
                    log.debug("Player hit for {} damage (hp {} → {})", b.owner.damage, hpBefore, player.hp);
                    burst(b.x, b.y, playerHit, 10, 190f);
                }
            } else {
                for (Shooter e : enemies) {
                    if (e.dead) continue;
                    if (e.bulletHits(b.x, b.y, BULLET_HIT_RADIUS)) {
                        b.dead = true;
                        e.takeDamage(b.owner.damage, b.nx, b.ny);
                        if (b.owner.isPlayer) damageDealt += b.owner.damage;
                        burst(b.x, b.y, enemyHit, 10, 190f);
                        if (e.dead) {
                            kills++;
                            log.debug("Enemy #{} killed | kills={}, damageDealt={}", enemies.indexOf(e, true), kills, damageDealt);
                        }
                        break;
                    }
                }
            }
            if (b.dead) bullets.removeIndex(i);
        }
    }

    /** Bullets that meet shatter each other: both vanish in a spark burst. */
    private void checkBulletCollisions() {
        float rr = BULLET_COLLIDE_RADIUS * BULLET_COLLIDE_RADIUS;
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet a = bullets.get(i);
            if (a.dead) {
                bullets.removeIndex(i);
                continue;
            }
            for (int j = i - 1; j >= 0; j--) {
                Bullet b = bullets.get(j);
                if (b.dead) continue;
                if (segSegDistSq(a.prevX, a.prevY, a.x, a.y,
                        b.prevX, b.prevY, b.x, b.y) <= rr) {
                    a.dead = true;
                    b.dead = true;
                    float mx = (a.x + b.x) * 0.5f;
                    float my = (a.y + b.y) * 0.5f;
                    burst(mx, my, a.color, 8, 240f);
                    burst(mx, my, b.color, 8, 240f);
                    break;
                }
            }
            if (a.dead) bullets.removeIndex(i);
        }
    }

    /** Squared distance between the two line segments of the bullets' travel this frame. */
    private static float segSegDistSq(float p1x, float p1y, float p2x, float p2y,
                                      float q1x, float q1y, float q2x, float q2y) {
        float d1x = p2x - p1x, d1y = p2y - p1y;
        float d2x = q2x - q1x, d2y = q2y - q1y;
        float rx = p1x - q1x, ry = p1y - q1y;

        float a = d1x * d1x + d1y * d1y;
        float e = d2x * d2x + d2y * d2y;
        float f = d2x * rx + d2y * ry;

        float s, t;
        if (a <= 1e-6f && e <= 1e-6f) {
            s = 0f;
            t = 0f;
        } else if (a <= 1e-6f) {
            s = 0f;
            t = MathUtils.clamp(f / e, 0f, 1f);
        } else {
            float c = d1x * rx + d1y * ry;
            float b = d1x * d2x + d1y * d2y;
            float denom = a * e - b * b;
            if (denom > 1e-6f) {
                s = MathUtils.clamp((b * f - c * e) / denom, 0f, 1f);
            } else {
                s = 0f;
            }
            t = (b * s + f) / e;
            if (t < 0f) {
                t = 0f;
                s = MathUtils.clamp(-c / a, 0f, 1f);
            } else if (t > 1f) {
                t = 1f;
                s = MathUtils.clamp((b - c) / a, 0f, 1f);
            }
        }

        float cx = p1x + d1x * s - q1x - d2x * t;
        float cy = p1y + d1y * s - q1y - d2y * t;
        return cx * cx + cy * cy;
    }

    private void resolveShooterCollisions() {
        if (player.dead) return;
        for (Shooter enemy : enemies) {
            if (enemy.dead) continue;
            float dx = player.x - enemy.x;
            float dy = player.y - enemy.y;
            float ox = (player.halfWidth() + enemy.halfWidth()) - Math.abs(dx);
            float oy = (player.halfHeight() + enemy.halfHeight()) - Math.abs(dy);
            if (ox > 0f && oy > 0f) {
                if (ox < oy) {
                    float dir = dx < 0f ? -1f : 1f;
                    player.x += dir * ox * 0.5f;
                    enemy.x -= dir * ox * 0.5f;
                    player.vx = 0f;
                    enemy.vx = 0f;
                } else {
                    float dir = dy < 0f ? -1f : 1f;
                    player.y += dir * oy * 0.5f;
                    enemy.y -= dir * oy * 0.5f;
                    player.vy = 0f;
                    enemy.vy = 0f;
                }
            }
        }
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
                    new Color(1f, 0.62f, 0.25f, 1f)));
        }
    }

    private float muzzleX(Shooter s) {
        return s.x + MathUtils.cos(s.angle) * Shooter.MUZZLE_OFFSET;
    }

    private float muzzleY(Shooter s) {
        return s.y + MathUtils.sin(s.angle) * Shooter.MUZZLE_OFFSET;
    }

    private void burst(float x, float y, Color c, int n, float speed) {
        for (int i = 0; i < n; i++) {
            particles.add(new Particle(x, y,
                    MathUtils.random() * MathUtils.PI2,
                    MathUtils.random(30f, speed),
                    MathUtils.random(0.22f, 0.45f),
                    MathUtils.random(1.5f, 3.2f), c));
        }
    }

    // ---------------- drawing ----------------

    private void drawBackground() {
        int bands = 36;
        float h = WORLD_H / bands;
        for (int i = 0; i < bands; i++) {
            float t = i / (bands - 1f);
            shape.setColor(lerpCol(bgTop, bgBottom, t));
            shape.rect(0f, WORLD_H - h * (i + 1), WORLD_W, h);
        }
    }

    private void drawGround() {
        shape.setColor(groundCol);
        shape.rect(0f, 0f, WORLD_W, GROUND_TOP);
        shape.setColor(groundLine);
        shape.rect(0f, GROUND_TOP, WORLD_W, 2f);
    }

    private void drawShadow(Shooter s) {
        float rx = s.halfWidth();
        float ry = s.halfHeight();
        float above = s.y - ry - GROUND_TOP;
        float sc = MathUtils.clamp(1f - above / 260f, 0.3f, 1f);
        float sw = rx * 1.7f * sc;
        float sh = 8f * sc;
        shape.setColor(0f, 0f, 0f, 0.16f * sc);
        shape.ellipse(s.x - sw * 0.5f, GROUND_TOP - 2f, sw, sh);
    }

    private void drawBullet(Bullet b) {
        shape.setColor(b.color.r, b.color.g, b.color.b, 0.22f);
        shape.circle(b.x, b.y, 10f);
        shape.setColor(b.color);
        shape.circle(b.x, b.y, 5.5f);
        shape.setColor(b.core);
        shape.circle(b.x, b.y, 2.6f);
    }

    private void drawFlash(MuzzleFlash f) {
        float p = MathUtils.clamp(f.life / f.maxLife, 0f, 1f);
        float flick = 0.75f + 0.5f * MathUtils.random();
        float cos = MathUtils.cos(f.angle);
        float sin = MathUtils.sin(f.angle);
        float px = -sin, py = cos;

        float len = 36f * p * flick;
        float tipX = f.x + cos * len;
        float tipY = f.y + sin * len;

        // outer flame cone: wide base at the muzzle, tip along the barrel
        shape.setColor(1f, 0.42f, 0.16f, 0.42f * p);
        shape.triangle(tipX, tipY,
                f.x + px * 9f * p, f.y + py * 9f * p,
                f.x - px * 9f * p, f.y - py * 9f * p);
        // inner yellow flame
        shape.setColor(1f, 0.85f, 0.38f, 0.55f * p);
        shape.triangle(f.x + cos * len * 0.5f, f.y + sin * len * 0.5f,
                f.x + px * 5f * p, f.y + py * 5f * p,
                f.x - px * 5f * p, f.y - py * 5f * p);
        // tinted glow hugging the muzzle
        shape.setColor(f.tint.r, f.tint.g, f.tint.b, 0.4f * p);
        shape.circle(f.x, f.y, 8f * p + 4f);
        // white-hot core
        shape.setColor(1f, 1f, 0.92f, 0.85f * p);
        shape.circle(f.x, f.y, 5f * p + 2f);
    }

    private void drawShooterFilled(Shooter s) {
        if (s.dead) return;
        float cos = MathUtils.cos(s.angle);
        float sin = MathUtils.sin(s.angle);
        shooterM.setToTranslation(s.x - cos * s.recoilVis * 10f, s.y - sin * s.recoilVis * 10f, 0f)
                .rotateRad(0f, 0f, 1f, s.angle + s.recoilVis * 0.16f)
                .scale(1.5f, 1.5f, 1f);
        shape.setTransformMatrix(shooterM);

        shape.setColor(s.body);
        roundedRect(-4f, -12f, 50f, 24f, 7f);
        shape.setColor(s.slide);
        shape.rect(-2f, 10f, 44f, 2.2f);
        shape.setColor(s.barrel);
        roundedRect(40f, -5f, 28f, 10f, 2.5f);
        shape.setColor(s.grip);
        shape.rect(64f, -4f, 6f, 8f);
        roundedRect(2f, -30f, 18f, 20f, 4f);
        shape.setColor(s.slide);
        shape.circle(55f, 12f, 2f);

        shape.setTransformMatrix(idM.idt());
    }

    private void drawShooterOutline(Shooter s) {
        if (s.dead) return;
        float cos = MathUtils.cos(s.angle);
        float sin = MathUtils.sin(s.angle);
        shooterM.setToTranslation(s.x - cos * s.recoilVis * 10f, s.y - sin * s.recoilVis * 10f, 0f)
                .rotateRad(0f, 0f, 1f, s.angle + s.recoilVis * 0.16f)
                .scale(1.5f, 1.5f, 1f);
        shape.setTransformMatrix(shooterM);

        shape.setColor(0f, 0f, 0f, 0.20f);
        shape.circle(32f, -2f, 7.5f);
        shape.setColor(s.slide.r, s.slide.g, s.slide.b, 0.6f);
        shape.line(0f, 9f, 22f, 9f);
        shape.line(0f, 7f, 22f, 7f);

        shape.setTransformMatrix(idM.idt());
    }

    /** Round-start invincibility bubble: a soft translucent dome over the player. */
    private void drawShieldFilled() {
        if (player.dead || player.invincibleTime <= 0f) return;
        float p = MathUtils.clamp(player.invincibleTime / INVINCIBLE_TIME, 0f, 1f);
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 26f);
        float rad = 102f + 18f * pulse;
        shape.setColor(shieldGlow.r, shieldGlow.g, shieldGlow.b, 0.2f * p);
        shape.circle(player.x, player.y, rad + 36f);
        shape.setColor(shieldCol.r, shieldCol.g, shieldCol.b, 0.50f * p);
        shape.circle(player.x, player.y, rad);
    }

    /** Pulsing rings that mark the boundary of the invincibility shield. */
    private void drawShieldRing() {
        if (player.dead || player.invincibleTime <= 0f) return;
        float p = MathUtils.clamp(player.invincibleTime / INVINCIBLE_TIME, 0f, 1f);
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 26f);
        float rad = 102f + 18f * pulse;
        shape.setColor(shieldCol.r, shieldCol.g, shieldCol.b, 0.80f * p);
        shape.circle(player.x, player.y, rad + 12f);
        shape.setColor(shieldGlow.r, shieldGlow.g, shieldGlow.b, 0.80f * p);
        shape.circle(player.x, player.y, rad - 12f);
    }

    private void drawParticle(Particle p) {
        float a = MathUtils.clamp(p.life / p.maxLife, 0f, 1f);
        shape.setColor(p.color.r, p.color.g, p.color.b, a);
        shape.circle(p.x, p.y, Math.max(0.4f, p.radius * a));
    }

    private void drawHealthBar(Shooter s) {
        float frac = MathUtils.clamp((float) s.hp / s.maxHp, 0f, 1f);
        float bw = 70f, bh = 9f;
        float bx = s.x - bw * 0.5f;
        float by = s.y + s.halfHeight() + 12f;
        shape.setColor(healthBg);
        roundedRect(bx, by, bw, bh, 4f);
        if (frac > 0.001f) {
            shape.setColor(lerpCol(healthLo, healthHi, frac));
            roundedRect(bx + 2f, by + 2f, (bw - 4f) * frac, bh - 4f, 2.5f);
        }
    }

    private void drawHealthBarBorder(Shooter s) {
        float bw = 70f, bh = 9f;
        float bx = s.x - bw * 0.5f;
        float by = s.y + s.halfHeight() + 12f;
        shape.setColor(textCol.r, textCol.g, textCol.b, 0.5f);
        shape.rect(bx, by, bw, bh);
    }

    private void drawHud() {
        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        if (gameOver) {
            font.getData().setScale(3.2f);
            font.setColor(textCol);
            String title = "GAME OVER";
            layout.setText(font, title);
            font.draw(batch, title, (WORLD_W - layout.width) * 0.5f, WORLD_H * 0.70f);

            font.getData().setScale(1.7f);
            font.setColor(hintCol);
            String killsStr = "KILLS: " + kills;
            String roundStr = "ROUNDS SURVIVED: " + round;
            String dmgStr = "DAMAGE DEALT: " + damageDealt;
            layout.setText(font, killsStr);
            font.draw(batch, killsStr, (WORLD_W - layout.width) * 0.5f, WORLD_H * 0.52f);
            layout.setText(font, roundStr);
            font.draw(batch, roundStr, (WORLD_W - layout.width) * 0.5f, WORLD_H * 0.45f);
            layout.setText(font, dmgStr);
            font.draw(batch, dmgStr, (WORLD_W - layout.width) * 0.5f, WORLD_H * 0.38f);

            font.getData().setScale(1.6f);
            font.setColor(hintCol);
            layout.setText(font, "Press R to restart");
            font.draw(batch, "Press R to restart", (WORLD_W - layout.width) * 0.5f, WORLD_H * 0.30f);
        } else {
            // top-left counters: enemies killed and current round
            font.getData().setScale(2.5f);
            font.setColor(hintCol);
            String roundStr = "ROUND " + round;
            layout.setText(font, roundStr);
            font.draw(batch, roundStr, 26f, WORLD_H - 22f);

            font.getData().setScale(1.5f);
            font.setColor(textCol);
            String killsStr = "KILLS " + kills;
            layout.setText(font, killsStr);
            font.draw(batch, killsStr, 30f, WORLD_H - 60f);

            font.getData().setScale(1.6f);
            font.setColor(hintCol);
            String hint = "Hold SPACE to shoot   ·   R to restart";
            layout.setText(font, hint);
            font.draw(batch, hint, (WORLD_W - layout.width) * 0.5f, 38f);

            if (player.invincibleTime > 0f) {
                font.getData().setScale(1.3f);
                font.setColor(shieldGlow);
                String shieldStr = "INVINCIBLE";
                layout.setText(font, shieldStr);
                font.draw(batch, shieldStr, player.x - layout.width * 0.5f, player.y + player.halfHeight() + 42f);
            }
        }

        batch.end();
    }

    /**
     * Round-start transition: a soft dark veil over the world with a
     * low-opacity "ROUND N" banner centered on screen. Purely visual —
     * the fight (and the player's controls) carry on underneath it.
     */
    private void drawRoundBanner() {
        if (gameOver || roundBannerTime <= 0f) return;
        // t: elapsed time since the round started (0 → ROUND_BANNER_TIME).
        // The veil fades in to 50% over the first 0.3s, settles to 20% over
        // the next 0.7s, then spends the final 2s easing away to fully
        // transparent. Every downward step is an ease-out curve — opacity
        // drops fast at first and tapers off slowly, so the handoff back
        // to the game feels gradual instead of abrupt.
        float t = ROUND_BANNER_TIME - roundBannerTime;
        float e; // shared envelope 0..1 that shapes veil, text and accents
        if (t < 0.3f) {
            float u = t / 0.3f;
            e = 1f - (1f - u) * (1f - u); // 0 → 1, banner pops in quickly
        } else if (t < 1.0f) {
            float u = (t - 0.3f) / 0.7f;
            e = 1f - 0.6f * (1f - (1f - u) * (1f - u)); // 1 → 0.4 (50% → 20% veil)
        } else {
            float u = (t - 1.0f) / 2.0f;
            e = 0.4f * (1f - u) * (1f - u); // 0.4 → 0, slow tail to fully transparent
        }
        if (e <= 0.001f) return;

        String title = "ROUND " + round;
        font.getData().setScale(3f);
        layout.setText(font, title);
        float titleW = layout.width;

        float cx = WORLD_W * 0.5f;
        float titleY = WORLD_H * 0.54f;
        float lineLen = 40f * e;
        float gap = titleW * 0.5f + 26f;
        float lineY = titleY - 18f; // vertical middle of the title glyphs

        // black veil peaking at 50% — clearly dimmed, never a blackout — with
        // two short mint accents growing in from the sides of the title.
        // Blending must be re-enabled here: drawHud()'s SpriteBatch.end()
        // disables GL_BLEND, and ShapeRenderer.begin() does not turn it back
        // on — with blending off, the veil's alpha is ignored and it would
        // render as a fully opaque blackout instead of a translucent dim.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0f, 0f, 0f, 0.5f * e);
        shape.rect(0f, 0f, WORLD_W, WORLD_H);
        shape.setColor(playerBody.r, playerBody.g, playerBody.b, 0.35f * e);
        roundedRect(cx - gap - lineLen, lineY, lineLen, 4f, 2f);
        roundedRect(cx + gap, lineY, lineLen, 4f, 2f);
        shape.end();

        // low-opacity, dead-center text: soft off-white title, faint subtitle
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        font.setColor(textCol.r, textCol.g, textCol.b, 0.6f * e);
        font.draw(batch, title, cx - layout.width * 0.5f, titleY);

        String sub = roundEnemies == 1 ? "1 ENEMY" : roundEnemies + " ENEMIES";
        font.getData().setScale(1.5f);
        layout.setText(font, sub);
        font.setColor(hintCol.r, hintCol.g, hintCol.b, 0.45f * e);
        font.draw(batch, sub, cx - layout.width * 0.5f, titleY - 56f);
        batch.end();
    }

    private Color lerpCol(Color a, Color b, float t) {
        lerpTmp.set(
                a.r + (b.r - a.r) * t,
                a.g + (b.g - a.g) * t,
                a.b + (b.b - a.b) * t,
                a.a + (b.a - a.a) * t);
        return lerpTmp;
    }

    /** Rounded rectangle drawn out of rects and circles (Filled mode only). */
    private void roundedRect(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) * 0.5f);
        shape.rect(x + r, y, w - 2f * r, h);
        shape.rect(x, y + r, w, h - 2f * r);
        shape.circle(x + r, y + r, r);
        shape.circle(x + w - r, y + r, r);
        shape.circle(x + r, y + h - r, r);
        shape.circle(x + w - r, y + h - r, r);
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void dispose() {
        shape.dispose();
        batch.dispose();
        font.dispose();
    }
}
