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

    private static final float ENEMY_TOLERANCE = 0.10f;
    private static final float DODGE_HORIZON = 0.5f;
    private static final float DODGE_COOLDOWN = 0.6f;
    private static final float BULLET_COLLIDE_RADIUS = 12f;
    private static final float BULLET_HIT_RADIUS = 4f;
    private static final int MAX_ENEMIES = 6;
    private static final float ENEMY_GROWTH = 1.05f;
    /** Read by {@link WorldRenderer} for the shield's fade-out. */
    static final float INVINCIBLE_TIME = 3f;
    /** Read by {@link WorldRenderer} for the round banner's timing. */
    static final float ROUND_BANNER_TIME = 2.5f;

    private WorldRenderer renderer;

    Shooter player;
    final Array<Shooter> enemies = new Array<>();
    final Array<Bullet> bullets = new Array<>();
    final Array<Particle> particles = new Array<>();
    final Array<MuzzleFlash> flashes = new Array<>();

    int round = 1;
    int kills = 0;
    int damageDealt = 0;
    boolean gameOver;
    float time;
    /** Counts down while the round-start transition banner is on screen. */
    float roundBannerTime = 0f;
    /** Enemy count of the current round, shown under the banner title. */
    int roundEnemies = 0;

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
                    Palette.enemyBody, Palette.enemyBarrel, Palette.enemyGrip, Palette.enemySlide,
                    Palette.enemyBullet, Palette.enemyBulletCore);
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
        renderer.render();
    }

    private void update(float dt) {
        if (gameOver) return;
        time += dt;
        if (roundBannerTime > 0f) roundBannerTime = Math.max(0f, roundBannerTime - dt);
        float invBefore = player.invincibleTime;
        player.update(dt);
        if (invBefore > 0f && player.invincibleTime <= 0f) {
            // the shield pops out with a spark burst when the grace period ends
            burst(player.x, player.y, Palette.shieldCol, 14, 240f);
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
            burst(muzzleX(player), muzzleY(player), Palette.smokeCol, 6, 110f);
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
                burst(muzzleX(enemy), muzzleY(enemy), Palette.smokeCol, 6, 110f);
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
                burst(muzzleX(enemy), muzzleY(enemy), Palette.smokeCol, 6, 110f);
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
            burst(muzzleX(enemy), muzzleY(enemy), Palette.smokeCol, 6, 110f);
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
        return bulletTimeToEnemy(b.x, b.y, b.vx, b.vy, enemy, BULLET_HIT_RADIUS);
    }

    /**
     * Static core of {@link #bulletTimeToEnemy(Bullet, Shooter)} so the
     * ray-vs-hitbox math is unit-testable without a live GameWorld.
     */
    static float bulletTimeToEnemy(float bx, float by, float bvx, float bvy,
                                   Shooter enemy, float hitRadius) {
        float cos = MathUtils.cos(enemy.angle);
        float sin = MathUtils.sin(enemy.angle);
        float dx = bx - enemy.x, dy = by - enemy.y;
        float lx = cos * dx + sin * dy;
        float ly = -sin * dx + cos * dy;
        float lvx = cos * bvx + sin * bvy;
        float lvy = -sin * bvx + cos * bvy;
        float best = -1f;
        for (float[] hb : Shooter.HITBOXES) {
            float t = hitTimeAlong(lx, ly, lvx, lvy,
                    hb[0] - hitRadius, hb[1] - hitRadius,
                    hb[2] + hitRadius, hb[3] + hitRadius);
            if (t >= 0f && (best < 0f || t < best)) best = t;
        }
        return best;
    }

    /** Time until a ray (px,py)+(vx,vy)*t enters the box, or -1 if it never does. */
    static float hitTimeAlong(float px, float py, float vx, float vy,
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
        return dodgeAimAngle(enemy.x, enemy.y, b.x, b.y, b.vx, b.vy);
    }

    /**
     * Static core of {@link #dodgeAimAngle(Shooter, Bullet)} so the aim math
     * is unit-testable without a live GameWorld.
     */
    static float dodgeAimAngle(float ex, float ey, float bx, float by, float bvx, float bvy) {
        float pushX = -bvy, pushY = bvx;
        float len = (float) Math.sqrt(pushX * pushX + pushY * pushY);
        if (len < 0.0001f) return 0f;
        pushX /= len;
        pushY /= len;
        float rx = ex - bx, ry = ey - by;
        if (pushX * rx + pushY * ry < 0f) {
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
                    burst(b.x, b.y, Palette.shieldCol, 8, 170f);
                    log.debug("Bullet absorbed by invincibility shield");
                } else {
                    int hpBefore = player.hp;
                    player.takeDamage(b.owner.damage, b.nx, b.ny);
                    log.debug("Player hit for {} damage (hp {} → {})", b.owner.damage, hpBefore, player.hp);
                    burst(b.x, b.y, Palette.playerHit, 10, 190f);
                }
            } else {
                for (Shooter e : enemies) {
                    if (e.dead) continue;
                    if (e.bulletHits(b.x, b.y, BULLET_HIT_RADIUS)) {
                        b.dead = true;
                        e.takeDamage(b.owner.damage, b.nx, b.ny);
                        if (b.owner.isPlayer) damageDealt += b.owner.damage;
                        burst(b.x, b.y, Palette.enemyHit, 10, 190f);
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
    static float segSegDistSq(float p1x, float p1y, float p2x, float p2y,
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
            resolveOverlap(player, enemy);
        }
    }

    /**
     * Pushes two overlapping shooters apart along the axis of least
     * penetration and zeroes their velocity on that axis. Static so it is
     * unit-testable without a live GameWorld.
     */
    static void resolveOverlap(Shooter a, Shooter b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        float ox = (a.halfWidth() + b.halfWidth()) - Math.abs(dx);
        float oy = (a.halfHeight() + b.halfHeight()) - Math.abs(dy);
        if (ox > 0f && oy > 0f) {
            if (ox < oy) {
                float dir = dx < 0f ? -1f : 1f;
                a.x += dir * ox * 0.5f;
                b.x -= dir * ox * 0.5f;
                a.vx = 0f;
                b.vx = 0f;
            } else {
                float dir = dy < 0f ? -1f : 1f;
                a.y += dir * oy * 0.5f;
                b.y -= dir * oy * 0.5f;
                a.vy = 0f;
                b.vy = 0f;
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
                    Palette.sparkCol));
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

    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    public void dispose() {
        renderer.dispose();
    }
}
