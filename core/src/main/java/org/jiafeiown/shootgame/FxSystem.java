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
}
