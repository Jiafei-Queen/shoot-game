package org.jiafeiown.shootgame;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Short-lived visual effects: particle sparks/smoke and muzzle flashes.
 * Owns the effect arrays, spawns muzzle effects and impact bursts, and
 * advances everything each frame. Pure data holder — needs no world
 * reference; callers pass in what they need.
 */
public class FxSystem {

    final Array<Particle> particles = new Array<>();
    final Array<MuzzleFlash> flashes = new Array<>();

    /** Advances all particles and flashes, removing the expired ones. */
    void update(float dt) {
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
    }

    void clear() {
        particles.clear();
        flashes.clear();
    }

    /** Muzzle flash plus a spray of hot sparks for one shot from {@code s}. */
    void muzzleEffects(Shooter s) {
        addFlash(s);
        burst(muzzleX(s), muzzleY(s), Palette.smokeCol, 6, 110f);
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

    /** Spawn-in effect for a materializing shooter at (x, y): a ring of
     *  motes converging inward from around the spawn point plus a soft
     *  outward puff, tinted with the shooter's own colors. */
    void spawnEffects(Shooter s) {
        // converging motes: placed on a circle around the spawn point and
        // given just enough speed to reach the center as they expire, so the
        // effect reads as the body being pulled together out of thin air
        for (int i = 0; i < 16; i++) {
            float a = MathUtils.random() * MathUtils.PI2;
            float d = MathUtils.random(70f, 160f);
            float life = MathUtils.random(0.55f, 0.95f);
            Particle p = new Particle(s.x + MathUtils.cos(a) * d, s.y + MathUtils.sin(a) * d,
                    a + MathUtils.PI, d / life * MathUtils.random(0.85f, 1.1f),
                    life, MathUtils.random(1.6f, 3.0f), Palette.enemyBody);
            particles.add(p);
        }
        // soft outward puff marking the moment of appearance
        burst(s.x, s.y, Palette.enemyBarrel, 10, 150f);
    }

    /** Death dissolve effect for a defeated enemy: a violent radial burst
     *  in the enemy's colors, plus slower embers that drift upward out of
     *  the wreck while the body fades away. */
    void deathEffects(Shooter s) {
        burst(s.x, s.y, Palette.enemyHit, 16, 280f);
        burst(s.x, s.y, Palette.enemyBullet, 10, 190f);
        for (int i = 0; i < 8; i++) {
            particles.add(new Particle(
                    s.x + MathUtils.random(-30f, 30f),
                    s.y + MathUtils.random(-14f, 14f),
                    MathUtils.PI2 * 0.25f + MathUtils.random(-0.5f, 0.5f), // upward cone
                    MathUtils.random(50f, 150f),
                    MathUtils.random(0.35f, 0.7f),
                    MathUtils.random(1.2f, 2.4f),
                    Palette.sparkCol));
        }
    }
}
