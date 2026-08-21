package org.jiafeiown.shootgame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Round progression and match statistics: current round, kill/damage tallies,
 * the round-start banner timer and the game-over flag.
 *
 * <p>Called by {@link GameWorld} once per frame after collisions: detects a
 * defeated player (→ {@link GameState#GAME_OVER}) or a cleared round (→ heal
 * the player and spawn the next wave). Reads and writes entity state through
 * its {@link GameWorld} reference.
 */
public class RoundManager {

    private static final Logger log = LogManager.getLogger(RoundManager.class);

    private static final int MAX_ENEMIES = 6;
    private static final float ENEMY_GROWTH = 1.05f;

    private final GameWorld world;

    int round = 1;
    int kills = 0;
    int damageDealt = 0;
    GameState state = GameState.PLAYING;
    /** Counts down while the round-start transition banner is on screen. */
    float roundBannerTime = 0f;
    /** Enemy count of the current round, shown under the banner title. */
    int roundEnemies = 0;

    public RoundManager(GameWorld world) {
        this.world = world;
    }

    boolean isGameOver() {
        return state == GameState.GAME_OVER;
    }

    /** Resets to round 1 and spawns the first wave. */
    void reset() {
        round = 1;
        kills = 0;
        damageDealt = 0;
        state = GameState.PLAYING;
        spawnRound();
    }

    /** Counts the round-start banner timer down toward zero. */
    void updateBanner(float dt) {
        if (roundBannerTime > 0f) roundBannerTime = Math.max(0f, roundBannerTime - dt);
    }

    /**
     * Called after collisions each frame: ends the game when the player is
     * defeated, otherwise advances to the next round when all enemies are dead.
     */
    void update() {
        // player died: freeze the world and show the settlement screen
        if (world.player.dead) {
            log.info("Game over: player defeated in round {} | kills={}, damageDealt={}", round, kills, damageDealt);
            state = GameState.GAME_OVER;
            return;
        }
        // all enemies of the round cleared: heal the player 30% and move on
        boolean allDead = true;
        for (Shooter e : world.enemies) {
            if (!e.dead) {
                allDead = false;
                break;
            }
        }
        if (allDead) {
            int healed = Math.round(world.player.maxHp * 0.30f);
            round++;
            world.player.hp = Math.min(world.player.maxHp, world.player.hp + healed);
            log.info("Round {} cleared | healed +{} HP ({} → {}) | starting round {}",
                    round - 1, healed, world.player.hp - healed, world.player.hp, round);
            spawnRound();
        }
    }

    /** Spawns this round's enemies: one more than the previous round (up to
     *  {@link #MAX_ENEMIES}), each with +5% max hp and bullet damage per round. */
    private void spawnRound() {
        world.enemies.clear();
        int count = Math.min(round, MAX_ENEMIES);
        float mult = (float) Math.pow(ENEMY_GROWTH, round - 1);
        int hp = Math.max(1, Math.round(Shooter.BASE_HP * mult));
        int dmg = Math.max(1, Math.round(Shooter.BULLET_DAMAGE * mult));
        for (int i = 0; i < count; i++) {
            float ex = WorldConfig.WORLD_W * (0.22f + 0.56f * i / Math.max(1f, count - 1f));
            Shooter e = new Shooter(false, ex, WorldConfig.GROUND_TOP + 80f, hp, 1.15f,
                    Palette.enemyBody, Palette.enemyBarrel, Palette.enemyGrip, Palette.enemySlide,
                    Palette.enemyBullet, Palette.enemyBulletCore);
            e.spin = 1.6f;
            e.damage = dmg;
            world.enemies.add(e);
        }
        // the player is briefly invincible at the top of every round
        world.player.invincibleTime = GameWorld.INVINCIBLE_TIME;
        roundEnemies = count;
        roundBannerTime = GameWorld.ROUND_BANNER_TIME;
        log.info("Round {} started: {} enemies (hp={}, dmg={})", round, count, hp, dmg);
    }
}
