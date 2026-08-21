package org.jiafeiown.shootgame;

import com.badlogic.gdx.math.MathUtils;

/**
 * Pure geometric helpers shared by the collision system and the enemy AI's
 * bullet-intercept math. All methods are static and run headless, so they are
 * unit-tested directly.
 */
public final class Geometry {

    private Geometry() {
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
}
