package org.jiafeiown.shootgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Draws the whole game: background, ground, shooters, bullets, particles,
 * muzzle flashes, the invincibility shield, health bars, HUD and the round
 * banner. Purely presentational — it only reads state out of {@link GameWorld}
 * and the entities, and never mutates game state.
 */
public class WorldRenderer {

    /** Game-over screen baselines, shared with the layout regression test:
     *  the total-time line and the restart hint sit close together at the
     *  bottom of the stats block and must never overlap (the hint is drawn
     *  at a smaller scale, but still needs a clear gap). */
    static final float GAME_OVER_TIME_Y = WorldConfig.WORLD_H * 0.31f;
    static final float GAME_OVER_HINT_Y = WorldConfig.WORLD_H * 0.20f;

    private final GameWorld world;

    private OrthographicCamera cam;
    private FitViewport viewport;
    private ShapeRenderer shape;
    private SpriteBatch batch;
    private BitmapFont font;

    private final Matrix4 shooterM = new Matrix4();
    private final Matrix4 idM = new Matrix4();
    private final Color lerpTmp = new Color();
    private final GlyphLayout layout = new GlyphLayout();

    public WorldRenderer(GameWorld world) {
        this.world = world;
    }

    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(false, WorldConfig.WORLD_W, WorldConfig.WORLD_H);
        viewport = new FitViewport(WorldConfig.WORLD_W, WorldConfig.WORLD_H, cam);
        shape = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void render() {
        viewport.apply();
        ScreenUtils.clear(Palette.bgBottom.r, Palette.bgBottom.g, Palette.bgBottom.b, 1f);
        shape.setProjectionMatrix(cam.combined);
        // Blending must be (re-)enabled every frame: on some backends the state
        // set once in create() is lost before the first frame, which would make
        // every translucent shape (shadows, flashes, the shield) render opaque.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Shooter player = world.player;

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setTransformMatrix(idM.idt());
        drawBackground();
        drawGround();
        if (!player.dead || player.isDying()) drawShadow(player);
        for (Shooter e : world.enemies) if (!e.dead || e.isDying()) drawShadow(e);
        for (Bullet b : world.bullets) drawBullet(b);
        for (MuzzleFlash f : world.fx.flashes) drawFlash(f);
        drawShooterFilled(player);
        for (Shooter e : world.enemies) {
            if (!e.dead || e.isDying()) {
                drawSpawnGlow(e);
                drawDeathFx(e);
                drawShooterFilled(e);
            }
        }
        drawShieldFilled();
        for (Particle p : world.fx.particles) drawParticle(p);
        if (!player.dead) drawHealthBar(player);
        for (Shooter e : world.enemies) if (!e.dead) drawHealthBar(e);
        if (world.rounds.isGameOver()) {
            shape.setColor(0f, 0f, 0f, 0.55f);
            shape.rect(0f, 0f, WorldConfig.WORLD_W, WorldConfig.WORLD_H);
        }
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setTransformMatrix(idM.idt());
        if (!world.rounds.isGameOver()) {
            drawShooterOutline(player);
            for (Shooter e : world.enemies) drawShooterOutline(e);
            drawShieldRing();
            if (!player.dead) drawHealthBarBorder(player);
            for (Shooter e : world.enemies) if (!e.dead) drawHealthBarBorder(e);
        }
        shape.end();

        drawHud();
        drawPauseButton();
        drawRoundBanner();
        if (world.rounds.isPaused()) drawPauseMenu();
        if (world.rounds.isMainMenu()) drawStartMenu();
    }

    // ---------------- drawing ----------------

    private void drawBackground() {
        int bands = 36;
        float h = WorldConfig.WORLD_H / bands;
        for (int i = 0; i < bands; i++) {
            float t = i / (bands - 1f);
            shape.setColor(lerpCol(Palette.bgTop, Palette.bgBottom, t));
            shape.rect(0f, WorldConfig.WORLD_H - h * (i + 1), WorldConfig.WORLD_W, h);
        }
    }

    private void drawGround() {
        shape.setColor(Palette.groundCol);
        shape.rect(0f, 0f, WorldConfig.WORLD_W, WorldConfig.GROUND_TOP);
        shape.setColor(Palette.groundLine);
        shape.rect(0f, WorldConfig.GROUND_TOP, WorldConfig.WORLD_W, 2f);
    }

    private void drawShadow(Shooter s) {
        float a = s.spawnAlpha();
        if (s.dead) a *= s.deathAlpha(); // corpse shadows dissolve with the body
        float rx = s.halfWidth();
        float ry = s.halfHeight();
        float above = s.y - ry - WorldConfig.GROUND_TOP;
        float sc = MathUtils.clamp(1f - above / 260f, 0.3f, 1f);
        float sw = rx * 1.7f * sc;
        float sh = 8f * sc;
        shape.setColor(0f, 0f, 0f, 0.16f * sc * a);
        shape.ellipse(s.x - sw * 0.5f, WorldConfig.GROUND_TOP - 2f, sw, sh);
    }

    /** Materialize glow around a shooter that is still fading in: a soft
     *  tinted disc whose radius contracts onto the body as the spawn
     *  progresses. Drawn in the main filled pass, where blending is already
     *  re-enabled every frame (see docs/libgdx-alpha-blending.md). */
    private void drawSpawnGlow(Shooter s) {
        if (!s.isSpawning()) return;
        float p = 1f - MathUtils.clamp(s.spawnTimer / Shooter.SPAWN_TIME, 0f, 1f); // 0 → 1
        float pulse = 0.5f + 0.5f * MathUtils.sin(world.time * 18f);
        float rad = s.halfWidth() * (2.6f - 1.4f * p) + 14f * pulse;
        // glow brightens toward the middle of the fade and dissolves at the end
        float env = MathUtils.sin(p * MathUtils.PI);
        shape.setColor(Palette.enemyBody.r, Palette.enemyBody.g, Palette.enemyBody.b, 0.16f * env);
        shape.circle(s.x, s.y, rad + 26f * (1f - p));
        shape.setColor(Palette.enemySlide.r, Palette.enemySlide.g, Palette.enemySlide.b, 0.30f * env);
        shape.circle(s.x, s.y, rad);
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

    /** Dissolve-out effects for a defeated enemy while its death animation
     *  plays: two expanding shockwave rings whose brightness follows a sine
     *  envelope (bright at first, gone by the end). Drawn in the main filled
     *  pass where blending is re-enabled every frame (see
     *  docs/libgdx-alpha-blending.md). */
    private void drawDeathFx(Shooter e) {
        if (!e.isDying()) return;
        float p = 1f - e.deathTimer / Shooter.DEATH_TIME; // 0 → 1 over the dissolve
        float env = MathUtils.sin(p * MathUtils.PI);
        shape.setColor(Palette.enemySlide.r, Palette.enemySlide.g, Palette.enemySlide.b, 0.45f * env);
        shape.circle(e.x, e.y, 40f + 130f * p);
        shape.setColor(Palette.enemyBody.r, Palette.enemyBody.g, Palette.enemyBody.b, 0.25f * env);
        shape.circle(e.x, e.y, 30f + 90f * p);
    }

    private void drawShooterFilled(Shooter s) {
        if (s.dead && !s.isDying()) return;
        float a = s.spawnAlpha();
        if (s.dead) a *= s.deathAlpha();
        // while dissolving the wreck tips over nose-down and sinks slightly
        float dieP = s.dead ? 1f - s.deathAlpha() : 0f;
        float dieTilt = 0.9f * dieP;
        float dieSink = 26f * dieP * dieP;
        float cos = MathUtils.cos(s.angle);
        float sin = MathUtils.sin(s.angle);
        shooterM.setToTranslation(s.x - cos * s.recoilVis * 10f,
                        s.y - sin * s.recoilVis * 10f - dieSink, 0f)
                .rotateRad(0f, 0f, 1f, s.angle + s.recoilVis * 0.16f + dieTilt)
                .scale(1.5f, 1.5f, 1f);
        shape.setTransformMatrix(shooterM);

        shape.setColor(s.body.r, s.body.g, s.body.b, s.body.a * a);
        roundedRect(-4f, -12f, 50f, 24f, 7f);
        shape.setColor(s.slide.r, s.slide.g, s.slide.b, s.slide.a * a);
        shape.rect(-2f, 10f, 44f, 2.2f);
        shape.setColor(s.barrel.r, s.barrel.g, s.barrel.b, s.barrel.a * a);
        roundedRect(40f, -5f, 28f, 10f, 2.5f);
        shape.setColor(s.grip.r, s.grip.g, s.grip.b, s.grip.a * a);
        shape.rect(64f, -4f, 6f, 8f);
        roundedRect(2f, -30f, 18f, 20f, 4f);
        shape.setColor(s.slide.r, s.slide.g, s.slide.b, s.slide.a * a);
        shape.circle(55f, 12f, 2f);

        shape.setTransformMatrix(idM.idt());
    }

    private void drawShooterOutline(Shooter s) {
        if (s.dead && !s.isDying()) return;
        float a = s.spawnAlpha();
        if (s.dead) a *= s.deathAlpha();
        float dieP = s.dead ? 1f - s.deathAlpha() : 0f;
        float dieTilt = 0.9f * dieP;
        float dieSink = 26f * dieP * dieP;
        float cos = MathUtils.cos(s.angle);
        float sin = MathUtils.sin(s.angle);
        shooterM.setToTranslation(s.x - cos * s.recoilVis * 10f,
                        s.y - sin * s.recoilVis * 10f - dieSink, 0f)
                .rotateRad(0f, 0f, 1f, s.angle + s.recoilVis * 0.16f + dieTilt)
                .scale(1.5f, 1.5f, 1f);
        shape.setTransformMatrix(shooterM);

        shape.setColor(0f, 0f, 0f, 0.20f * a);
        shape.circle(32f, -2f, 7.5f);
        shape.setColor(s.slide.r, s.slide.g, s.slide.b, 0.6f * a);
        shape.line(0f, 9f, 22f, 9f);
        shape.line(0f, 7f, 22f, 7f);

        shape.setTransformMatrix(idM.idt());
    }

    /** Round-start invincibility bubble: a soft translucent dome over the player. */
    private void drawShieldFilled() {
        Shooter player = world.player;
        if (player.dead || player.invincibleTime <= 0f) return;
        float p = MathUtils.clamp(player.invincibleTime / GameWorld.INVINCIBLE_TIME, 0f, 1f);
        float pulse = 0.5f + 0.5f * MathUtils.sin(world.time * 26f);
        float rad = 102f + 18f * pulse;
        shape.setColor(Palette.shieldGlow.r, Palette.shieldGlow.g, Palette.shieldGlow.b, 0.2f * p);
        shape.circle(player.x, player.y, rad + 36f);
        shape.setColor(Palette.shieldCol.r, Palette.shieldCol.g, Palette.shieldCol.b, 0.50f * p);
        shape.circle(player.x, player.y, rad);
    }

    /** Pulsing rings that mark the boundary of the invincibility shield. */
    private void drawShieldRing() {
        Shooter player = world.player;
        if (player.dead || player.invincibleTime <= 0f) return;
        float p = MathUtils.clamp(player.invincibleTime / GameWorld.INVINCIBLE_TIME, 0f, 1f);
        float pulse = 0.5f + 0.5f * MathUtils.sin(world.time * 26f);
        float rad = 102f + 18f * pulse;
        shape.setColor(Palette.shieldCol.r, Palette.shieldCol.g, Palette.shieldCol.b, 0.80f * p);
        shape.circle(player.x, player.y, rad + 12f);
        shape.setColor(Palette.shieldGlow.r, Palette.shieldGlow.g, Palette.shieldGlow.b, 0.80f * p);
        shape.circle(player.x, player.y, rad - 12f);
    }

    private void drawParticle(Particle p) {
        float a = MathUtils.clamp(p.life / p.maxLife, 0f, 1f);
        shape.setColor(p.color.r, p.color.g, p.color.b, a);
        shape.circle(p.x, p.y, Math.max(0.4f, p.radius * a));
    }

    private void drawHealthBar(Shooter s) {
        float a = s.spawnAlpha();
        float frac = MathUtils.clamp((float) s.hp / s.maxHp, 0f, 1f);
        float bw = 70f, bh = 9f;
        float bx = s.x - bw * 0.5f;
        float by = s.y + s.halfHeight() + 12f;
        shape.setColor(Palette.healthBg.r, Palette.healthBg.g, Palette.healthBg.b, Palette.healthBg.a * a);
        roundedRect(bx, by, bw, bh, 4f);
        if (frac > 0.001f) {
            Color c = lerpCol(Palette.healthLo, Palette.healthHi, frac);
            shape.setColor(c.r, c.g, c.b, c.a * a);
            roundedRect(bx + 2f, by + 2f, (bw - 4f) * frac, bh - 4f, 2.5f);
        }
    }

    private void drawHealthBarBorder(Shooter s) {
        float a = s.spawnAlpha();
        float bw = 70f, bh = 9f;
        float bx = s.x - bw * 0.5f;
        float by = s.y + s.halfHeight() + 12f;
        shape.setColor(Palette.textCol.r, Palette.textCol.g, Palette.textCol.b, 0.5f * a);
        shape.rect(bx, by, bw, bh);
    }

    private void drawHud() {
        // stays up during the slow-mo wind-down; only steps aside once the
        // pause screen is fully opaque and the world has stopped (or the
        // start screen is showing)
        if (world.rounds.isMainMenu() || world.rounds.isFullyPaused()) return;
        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        if (world.rounds.isGameOver()) {
            font.getData().setScale(3.2f);
            font.setColor(Palette.textCol);
            String title = "GAME OVER";
            layout.setText(font, title);
            font.draw(batch, title, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.70f);

            font.getData().setScale(1.7f);
            font.setColor(Palette.hintCol);
            String killsStr = "KILLS: " + world.rounds.kills;
            String roundStr = "ROUNDS SURVIVED: " + world.rounds.round;
            String dmgStr = "DAMAGE DEALT: " + world.rounds.damageDealt;
            layout.setText(font, killsStr);
            font.draw(batch, killsStr, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.52f);
            layout.setText(font, roundStr);
            font.draw(batch, roundStr, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.45f);
            layout.setText(font, dmgStr);
            font.draw(batch, dmgStr, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.38f);

            // TOTAL TIME estadísticas
            int minutes = (int) (world.time / 60f);
            int seconds = (int) world.time % 60;
            // GWT's Java emulation has no String.format, so pad by hand
            String timeStr = "TIME  " + (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
            font.getData().setScale(1.7f);
            layout.setText(font, timeStr);
            font.setColor(Palette.hintCol);
            font.draw(batch, timeStr, (WorldConfig.WORLD_W - layout.width) * 0.5f, GAME_OVER_TIME_Y);

            font.getData().setScale(1.6f);

            font.setColor(Palette.hintCol);
            String restartHint = GameWorld.isTouchDevice() ? "Tap to restart" : "Press R to restart";
            layout.setText(font, restartHint);
            // well below the time line: the two must never overlap (see the
            // GameOverLayoutTest regression guard)
            font.draw(batch, restartHint, (WorldConfig.WORLD_W - layout.width) * 0.5f, GAME_OVER_HINT_Y);
        } else {
            // top-left counters: enemies killed and current round
            font.getData().setScale(2.5f);
            font.setColor(Palette.hintCol);
            String roundStr = "ROUND " + world.rounds.round;
            layout.setText(font, roundStr);
            font.draw(batch, roundStr, 26f, WorldConfig.WORLD_H - 22f);

            font.getData().setScale(1.5f);
            font.setColor(Palette.textCol);
            String killsStr = "KILLS " + world.rounds.kills;
            layout.setText(font, killsStr);
            font.draw(batch, killsStr, 30f, WorldConfig.WORLD_H - 60f);

            font.getData().setScale(1.6f);
            font.setColor(Palette.hintCol);
            String hint = GameWorld.isTouchDevice()
                    ? "Hold screen to shoot   ·   Tap top-right to pause"
                    : "Hold SPACE to shoot   ·   R to restart";
            layout.setText(font, hint);
            font.draw(batch, hint, (WorldConfig.WORLD_W - layout.width) * 0.5f, 38f);

            Shooter player = world.player;
            if (player.invincibleTime > 0f) {
                font.getData().setScale(1.3f);
                font.setColor(Palette.shieldGlow);
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
        if (world.rounds.isMainMenu() || world.rounds.isFullyPaused()) return;
        if (world.rounds.isGameOver() || world.rounds.roundBannerTime <= 0f) return;
        // t: elapsed time since the round started (0 → ROUND_BANNER_TIME).
        // The veil fades in to 50% over the first 0.3s, settles to 20% over
        // the next 0.7s, then spends the final 2s easing away to fully
        // transparent. Every downward step is an ease-out curve — opacity
        // drops fast at first and tapers off slowly, so the handoff back
        // to the game feels gradual instead of abrupt.
        float t = GameWorld.ROUND_BANNER_TIME - world.rounds.roundBannerTime;
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

        String title = "ROUND " + world.rounds.round;
        font.getData().setScale(3f);
        layout.setText(font, title);
        float titleW = layout.width;

        float cx = WorldConfig.WORLD_W * 0.5f;
        float titleY = WorldConfig.WORLD_H * 0.54f;
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
        shape.rect(0f, 0f, WorldConfig.WORLD_W, WorldConfig.WORLD_H);
        shape.setColor(Palette.playerBody.r, Palette.playerBody.g, Palette.playerBody.b, 0.35f * e);
        roundedRect(cx - gap - lineLen, lineY, lineLen, 4f, 2f);
        roundedRect(cx + gap, lineY, lineLen, 4f, 2f);
        shape.end();

        // low-opacity, dead-center text: soft off-white title, faint subtitle
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        font.setColor(Palette.textCol.r, Palette.textCol.g, Palette.textCol.b, 0.6f * e);
        font.draw(batch, title, cx - layout.width * 0.5f, titleY);

        String sub = world.rounds.roundEnemies == 1 ? "1 ENEMY" : world.rounds.roundEnemies + " ENEMIES";
        font.getData().setScale(1.5f);
        layout.setText(font, sub);
        font.setColor(Palette.hintCol.r, Palette.hintCol.g, Palette.hintCol.b, 0.45f * e);
        font.draw(batch, sub, cx - layout.width * 0.5f, titleY - 56f);
        batch.end();
    }

    /** Pause overlay: dim veil over the slowing world, title, and the three
     *  clickable options. The hovered option is picked out in the player's mint.
     *  The whole page fades in linearly from 0% as the world winds down and
     *  fades out again over the resume transition (see
     *  {@link RoundManager#pauseMenuAlpha()}). */
    private void drawPauseMenu() {
        float fade = world.rounds.pauseMenuAlpha();
        if (fade <= 0.001f) return;
        // Blending must be re-enabled: the SpriteBatch from drawHud()/banner
        // leaves GL_BLEND off, and without it the veil renders as an opaque
        // blackout instead of a translucent dim.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.setTransformMatrix(idM.idt());
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0f, 0f, 0f, 0.55f * fade);
        shape.rect(0f, 0f, WorldConfig.WORLD_W, WorldConfig.WORLD_H);
        shape.end();

        float[] mp = unproject(Gdx.input.getX(), Gdx.input.getY());
        int hover = pauseOptionAt(mp[0], mp[1]);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        font.getData().setScale(3f);
        font.setColor(Palette.textCol.r, Palette.textCol.g, Palette.textCol.b, fade);
        String title = "PAUSED";
        layout.setText(font, title);
        font.draw(batch, title, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.70f);

        font.getData().setScale(GameWorld.PAUSE_OPTION_SCALE);
        for (int i = 0; i < GameWorld.PAUSE_OPTIONS.length; i++) {
            layout.setText(font, GameWorld.PAUSE_OPTIONS[i]);
            float ox = (WorldConfig.WORLD_W - layout.width) * 0.5f;
            float oy = GameWorld.PAUSE_MENU_START_Y - i * GameWorld.PAUSE_OPTION_SPACING;
            Color c = i == hover ? Palette.playerBody : Palette.hintCol;
            font.setColor(c.r, c.g, c.b, fade);
            font.draw(batch, GameWorld.PAUSE_OPTIONS[i], ox, oy);
        }

        font.getData().setScale(1.3f);
        font.setColor(Palette.hintCol.r, Palette.hintCol.g, Palette.hintCol.b, 0.7f * fade);
        String hint = GameWorld.isTouchDevice() ? "Tap an option" : "ESC to resume";
        layout.setText(font, hint);
        font.draw(batch, hint, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.16f);

        batch.end();
    }

    /** Top-right pause button: a rounded square with the classic "II" pause
     *  bars. A tap does what ESC does (see {@link GameWorld#handleInput()}).
     *  Drawn while the HUD is up, so it stays visible through the slow-mo
     *  wind-down and steps aside on the game-over and fully-paused screens. */
    private void drawPauseButton() {
        if (world.rounds.isMainMenu() || world.rounds.isGameOver() || world.rounds.isFullyPaused()) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0f, 0f, 0f, 0.38f);
        roundedRect(GameWorld.PAUSE_BTN_X, GameWorld.PAUSE_BTN_Y,
                GameWorld.PAUSE_BTN_SIZE, GameWorld.PAUSE_BTN_SIZE, 12f);
        shape.setColor(Palette.textCol);
        float barW = 6f, barH = 26f;
        float cx = GameWorld.PAUSE_BTN_X + GameWorld.PAUSE_BTN_SIZE * 0.5f;
        float cy = GameWorld.PAUSE_BTN_Y + GameWorld.PAUSE_BTN_SIZE * 0.5f;
        shape.rect(cx - barW - 3f, cy - barH * 0.5f, barW, barH);
        shape.rect(cx + 3f, cy - barH * 0.5f, barW, barH);
        shape.end();
    }

    /** Start screen: a dim veil over the empty field, the game title in the
     *  upper-middle and a START GAME button in the lower-middle. The button is
     *  picked out in the player's mint while hovered, like the pause options. */
    private void drawStartMenu() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.setTransformMatrix(idM.idt());
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0f, 0f, 0f, 0.45f);
        shape.rect(0f, 0f, WorldConfig.WORLD_W, WorldConfig.WORLD_H);

        float[] mp = unproject(Gdx.input.getX(), Gdx.input.getY());
        boolean hover = startButtonAt(mp[0], mp[1]);
        float bx = GameWorld.START_BTN_CX - GameWorld.START_BTN_W * 0.5f;
        float by = GameWorld.START_BTN_CY - GameWorld.START_BTN_H * 0.5f;
        // drop shadow under the button, then the button itself
        shape.setColor(0f, 0f, 0f, 0.35f);
        roundedRect(bx, by - 5f, GameWorld.START_BTN_W, GameWorld.START_BTN_H, 16f);
        if (hover) {
            shape.setColor(Palette.playerBody.r, Palette.playerBody.g, Palette.playerBody.b, 0.95f);
        } else {
            shape.setColor(0.20f, 0.25f, 0.31f, 0.95f);
        }
        roundedRect(bx, by, GameWorld.START_BTN_W, GameWorld.START_BTN_H, 16f);
        shape.end();

        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        // title, upper-middle
        font.getData().setScale(4.5f);
        font.setColor(Palette.playerBody);
        String title = "ShootGame";
        layout.setText(font, title);
        font.draw(batch, title, (WorldConfig.WORLD_W - layout.width) * 0.5f, GameWorld.START_TITLE_Y);

        // small subtitle under the title
        font.getData().setScale(1.5f);
        font.setColor(Palette.hintCol);
        String sub = "PISTOL DUEL SURVIVAL";
        layout.setText(font, sub);
        font.draw(batch, sub, (WorldConfig.WORLD_W - layout.width) * 0.5f, GameWorld.START_TITLE_Y - 52f);

        // START GAME button label, lower-middle
        font.getData().setScale(2.4f);
        font.setColor(hover ? Palette.playerSlide : Palette.textCol);
        String label = "START GAME";
        layout.setText(font, label);
        font.draw(batch, label,
                (WorldConfig.WORLD_W - layout.width) * 0.5f,
                GameWorld.START_BTN_CY + layout.height * 0.5f);

        // input hint below the button
        font.getData().setScale(1.3f);
        font.setColor(Palette.hintCol);
        String hint = GameWorld.isTouchDevice() ? "TAP TO START" : "ENTER / CLICK TO START";
        layout.setText(font, hint);
        font.draw(batch, hint, (WorldConfig.WORLD_W - layout.width) * 0.5f, GameWorld.START_BTN_CY - 64f);

        batch.end();
    }

    /** True when the world-space point (x, y) lies on the START GAME button. */
    boolean startButtonAt(float x, float y) {
        float hw = GameWorld.START_BTN_W * 0.5f;
        float hh = GameWorld.START_BTN_H * 0.5f;
        return x >= GameWorld.START_BTN_CX - hw && x <= GameWorld.START_BTN_CX + hw
                && y >= GameWorld.START_BTN_CY - hh && y <= GameWorld.START_BTN_CY + hh;
    }

    /**
     * Pause-menu hit test: index of the option whose box contains the world
     * point (x, y), or -1 when the cursor is outside every option. Read-only
     * measurement — the click is acted upon by {@link GameWorld}.
     */
    int pauseOptionAt(float x, float y) {
        font.getData().setScale(GameWorld.PAUSE_OPTION_SCALE);
        float pad = 14f;
        for (int i = 0; i < GameWorld.PAUSE_OPTIONS.length; i++) {
            layout.setText(font, GameWorld.PAUSE_OPTIONS[i]);
            float ox = (WorldConfig.WORLD_W - layout.width) * 0.5f;
            float oy = GameWorld.PAUSE_MENU_START_Y - i * GameWorld.PAUSE_OPTION_SPACING;
            if (x >= ox - pad && x <= ox + layout.width + pad
                    && y >= oy - layout.height - pad && y <= oy + pad) {
                return i;
            }
        }
        return -1;
    }

    /** True when the world-space point (x, y) lies on the pause button. */
    boolean pauseButtonAt(float x, float y) {
        return x >= GameWorld.PAUSE_BTN_X && x <= GameWorld.PAUSE_BTN_X + GameWorld.PAUSE_BTN_SIZE
                && y >= GameWorld.PAUSE_BTN_Y && y <= GameWorld.PAUSE_BTN_Y + GameWorld.PAUSE_BTN_SIZE;
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

    public OrthographicCamera getCam() { return cam; }

    /** Converts a screen/touch position (origin top-left, as reported by
     *  {@code Gdx.input.getX()/getY()}) into world coordinates. libGDX's
     *  {@code Camera.unproject} already expects touch coordinates and flips the
     *  Y axis internally, so no manual flip is needed; the viewport bounds are
     *  passed through so the mapping stays exact even when the window is
     *  letterboxed by the {@link FitViewport}. */
    public float[] unproject(float screenX, float screenY) {
        Vector3 v = new Vector3(screenX, screenY, 0f);
        cam.unproject(v, viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());
        return new float[]{v.x, v.y};
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
