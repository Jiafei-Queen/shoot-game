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
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Draws the whole game: background, ground, shooters, bullets, particles,
 * muzzle flashes, the invincibility shield, health bars, HUD and the round
 * banner. Purely presentational — it only reads state out of {@link GameWorld}
 * and the entities, and never mutates game state.
 */
public class WorldRenderer {

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
        if (!player.dead) drawShadow(player);
        for (Shooter e : world.enemies) if (!e.dead) drawShadow(e);
        for (Bullet b : world.bullets) drawBullet(b);
        for (MuzzleFlash f : world.fx.flashes) drawFlash(f);
        drawShooterFilled(player);
        for (Shooter e : world.enemies) drawShooterFilled(e);
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
        drawRoundBanner();
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
        float rx = s.halfWidth();
        float ry = s.halfHeight();
        float above = s.y - ry - WorldConfig.GROUND_TOP;
        float sc = MathUtils.clamp(1f - above / 260f, 0.3f, 1f);
        float sw = rx * 1.7f * sc;
        float sh = 8f * sc;
        shape.setColor(0f, 0f, 0f, 0.16f * sc);
        shape.ellipse(s.x - sw * 0.5f, WorldConfig.GROUND_TOP - 2f, sw, sh);
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
        float frac = MathUtils.clamp((float) s.hp / s.maxHp, 0f, 1f);
        float bw = 70f, bh = 9f;
        float bx = s.x - bw * 0.5f;
        float by = s.y + s.halfHeight() + 12f;
        shape.setColor(Palette.healthBg);
        roundedRect(bx, by, bw, bh, 4f);
        if (frac > 0.001f) {
            shape.setColor(lerpCol(Palette.healthLo, Palette.healthHi, frac));
            roundedRect(bx + 2f, by + 2f, (bw - 4f) * frac, bh - 4f, 2.5f);
        }
    }

    private void drawHealthBarBorder(Shooter s) {
        float bw = 70f, bh = 9f;
        float bx = s.x - bw * 0.5f;
        float by = s.y + s.halfHeight() + 12f;
        shape.setColor(Palette.textCol.r, Palette.textCol.g, Palette.textCol.b, 0.5f);
        shape.rect(bx, by, bw, bh);
    }

    private void drawHud() {
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
            String timeStr = String.format("%02d:%02d", minutes, seconds);
            font.getData().setScale(1.7f);
            layout.setText(font, timeStr);
            font.setColor(Palette.hintCol);
            font.draw(batch, timeStr, (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.31f);

            font.getData().setScale(1.6f);

            font.setColor(Palette.hintCol);
            layout.setText(font, "Press R to restart");
            font.draw(batch, "Press R to restart", (WorldConfig.WORLD_W - layout.width) * 0.5f, WorldConfig.WORLD_H * 0.30f);
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
            String hint = "Hold SPACE to shoot   ·   R to restart";
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
