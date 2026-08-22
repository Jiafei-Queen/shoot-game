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

    /** Pause menu wind-down: after ESC the world doesn't freeze dead, it
     *  eases through a slow-mo curve — 1x → 0.2x over the first
     *  {@value #PAUSE_SLOWMO_TIME}s, then 0.2x → 0x (fully frozen) by
     *  {@value #PAUSE_TRANSITION_TIME}s — while the pause screen fades in over
     *  the same window. */
    static final float PAUSE_TRANSITION_TIME = 1f;
    static final float PAUSE_SLOWMO_TIME = 0.3f;
    static final float PAUSE_SLOWMO_SCALE = 0.2f;

    /** Resume fade-out: leaving the pause menu holds it essentially opaque for
     *  the first {@value #RESUME_HOLD_TIME}s, then fades it away by
     *  {@value #RESUME_TRANSITION_TIME}s — only then does the game actually
     *  resume. The world stays frozen for the whole fade-out. */
    static final float RESUME_TRANSITION_TIME = 0.5f;
    static final float RESUME_HOLD_TIME = 0.2f;

    private final GameWorld world;

    int round = 1;
    int kills = 0;
    int damageDealt = 0;
    GameState state = GameState.PLAYING;
    /** Counts down while the round-start transition banner is on screen. */
    float roundBannerTime = 0f;
    /** Enemy count of the current round, shown under the banner title. */
    int roundEnemies = 0;
    /** Seconds since the pause menu opened, 0 → {@link #PAUSE_TRANSITION_TIME}.
     *  Drives the slow-mo time scale ({@link #pauseTimeScale()}) and the menu
     *  fade-in ({@link #pauseFade()}). Reset on every pause/resume. */
    float pauseTransition = 0f;
    /** True while the pause screen is fading out after a resume request; the
     *  world stays frozen until the fade completes and {@link #resume()} flips
     *  the state back to PLAYING. */
    boolean resuming = false;
    /** Seconds into the resume fade-out, 0 → {@link #RESUME_TRANSITION_TIME}. */
    float resumeProgress = 0f;
    /** Kills/damage tallies when the current round started; restoring them on
     *  a round restart discards the stats earned in the aborted attempt. */
    private int killsAtRoundStart;
    private int damageAtRoundStart;
    /** Player state when the current round started, restored by
     *  {@link #restartRound()} so a restarted round plays out from the exact
     *  same opening: position, muzzle orientation, velocity and hp. */
    private float startPlayerX, startPlayerY, startPlayerAngle, startPlayerVx, startPlayerVy;
    private int startPlayerHp;

    public RoundManager(GameWorld world) {
        this.world = world;
    }

    boolean isGameOver() {
        return state == GameState.GAME_OVER;
    }

    boolean isMainMenu() {
        return state == GameState.MAIN_MENU;
    }

    /** Returns to the start screen from the pause menu, abandoning the match
     *  in progress. The menu's field is cleared by {@link GameWorld#toMenu()};
     *  starting a new game from the menu resets every stat. */
    void toMenu() {
        state = GameState.MAIN_MENU;
        pauseTransition = 0f;
        resuming = false;
        resumeProgress = 0f;
        world.audio.stopPauseLoop();
    }

    boolean isPaused() {
        return state == GameState.PAUSED;
    }

    void pause() {
        state = GameState.PAUSED;
        pauseTransition = 0f;
        resuming = false;
        resumeProgress = 0f;
        world.audio.startPauseLoop();
    }

    /** Instant resume, no fade-out. Used when a resume is requested while the
     *  world is still winding down (the menu was barely visible anyway) and by
     *  {@link #advanceResume(float)} once the fade-out has completed. */
    void resume() {
        state = GameState.PLAYING;
        pauseTransition = 0f;
        resuming = false;
        resumeProgress = 0f;
        world.audio.stopPauseLoop();
    }

    /** Starts the 0.5s fade-out of the pause screen; the game resumes once the
     *  menu has fully faded. If the pause was still winding down (slow-mo not
     *  yet at a standstill) the resume is instant instead — there is no fully
     *  opaque menu to fade away. */
    void beginResume() {
        if (!isFullyPaused()) {
            resume();
            return;
        }
        resuming = true;
        resumeProgress = 0f;
        world.audio.stopPauseLoop();
    }

    /** The player changed their mind mid-fade-out: snap back to the fully
     *  paused, opaque menu (the world never unfroze). */
    void cancelResume() {
        resuming = false;
        resumeProgress = 0f;
        world.audio.startPauseLoop();
    }

    /** Advances the resume fade-out by real time; flips to PLAYING once the
     *  menu has fully faded away. Returns whether the transition is still
     *  running — while true the world stays frozen. */
    boolean advanceResume(float dt) {
        resumeProgress = Math.min(RESUME_TRANSITION_TIME, resumeProgress + dt);
        if (resumeProgress >= RESUME_TRANSITION_TIME) {
            resume();
            return false;
        }
        return true;
    }

    boolean isResuming() {
        return resuming;
    }

    /** Ends the match immediately and shows the settlement screen, as if the
     *  player had been defeated. The player is marked dead so the renderer
     *  drops the sprite, exactly like the defeat flow. */
    void endGame() {
        log.info("Game ended early by player | round={}, kills={}, damageDealt={}", round, kills, damageDealt);
        world.player.dead = true;
        state = GameState.GAME_OVER;
        pauseTransition = 0f;
        resuming = false;
        resumeProgress = 0f;
        world.audio.stopPauseLoop();
        world.audio.playGameOver();
    }

    /** Restarts the current round from its very beginning: the player is put
     *  back to the position/orientation/velocity/hp recorded when the round
     *  started, every bullet and effect from the aborted attempt is discarded,
     *  the enemies respawn at their opening spots, and the match stats roll
     *  back to where they stood when the round began. */
    void restartRound() {
        state = GameState.PLAYING;
        pauseTransition = 0f;
        resuming = false;
        resumeProgress = 0f;
        world.audio.stopPauseLoop();
        // roll back the aborted attempt's tallies before respawning, so the
        // fresh round's snapshot records the restored values
        kills = killsAtRoundStart;
        damageDealt = damageAtRoundStart;
        world.player.x = startPlayerX;
        world.player.y = startPlayerY;
        world.player.angle = startPlayerAngle;
        world.player.vx = startPlayerVx;
        world.player.vy = startPlayerVy;
        world.player.hp = startPlayerHp;
        // a restarted round opens on an empty field: no bullets in flight, no
        // lingering particles or muzzle flashes from the attempt
        world.bullets.clear();
        world.fx.clear();
        log.info("Round {} restarted | player back to start (hp {}), kills/damage rolled back to {}/{}",
                round, startPlayerHp, kills, damageDealt);
        spawnRound();
    }

    /** Resets to round 1 and spawns the first wave. */
    void reset() {
        round = 1;
        kills = 0;
        damageDealt = 0;
        state = GameState.PLAYING;
        pauseTransition = 0f;
        resuming = false;
        resumeProgress = 0f;
        world.audio.stopPauseLoop();
        spawnRound();
    }

    /** Advances the pause transition by real time and returns the world time
     *  scale to apply this frame: 1x at the moment ESC is pressed, easing down
     *  to 0.2x after {@link #PAUSE_SLOWMO_TIME} and 0x — fully frozen — by
     *  {@link #PAUSE_TRANSITION_TIME}. */
    float advancePauseTransition(float dt) {
        pauseTransition = Math.min(PAUSE_TRANSITION_TIME, pauseTransition + dt);
        return pauseTimeScale();
    }

    /** World time scale along the pause transition curve: linear 1x → 0.2x
     *  over the first 0.3s, then linear 0.2x → 0x by 1s. */
    float pauseTimeScale() {
        float t = pauseTransition;
        if (t >= PAUSE_TRANSITION_TIME) return 0f;
        if (t < PAUSE_SLOWMO_TIME) {
            return 1f - (1f - PAUSE_SLOWMO_SCALE) * (t / PAUSE_SLOWMO_TIME); // 1x → 0.2x
        }
        return PAUSE_SLOWMO_SCALE * (1f - (t - PAUSE_SLOWMO_TIME)
                / (PAUSE_TRANSITION_TIME - PAUSE_SLOWMO_TIME));              // 0.2x → 0x
    }

    /** Pause screen opacity while winding down: a linear fade-in across the
     *  whole transition. The combined {@link #pauseMenuAlpha()} switches to the
     *  resume fade-out curve once a resume is requested. */
    float pauseFade() {
        return Math.min(1f, pauseTransition / PAUSE_TRANSITION_TIME);
    }

    /** Current pause screen opacity, 0..1: fades in linearly across the
     *  wind-down, holds at 1 while fully paused, and on resume holds essentially
     *  opaque for the first {@value #RESUME_HOLD_TIME}s before fading away by
     *  {@value #RESUME_TRANSITION_TIME}s. */
    float pauseMenuAlpha() {
        if (resuming) {
            float r = resumeProgress;
            if (r >= RESUME_TRANSITION_TIME) return 0f;
            if (r < RESUME_HOLD_TIME) return 1f;
            return 1f - (r - RESUME_HOLD_TIME) / (RESUME_TRANSITION_TIME - RESUME_HOLD_TIME);
        }
        return pauseFade();
    }

    /** True once the transition has finished: the world is fully frozen and
     *  the pause screen is opaque. Until then the HUD/round banner stay up and
     *  the world keeps moving at the slow-mo scale. */
    boolean isFullyPaused() {
        return state == GameState.PAUSED && pauseTransition >= PAUSE_TRANSITION_TIME;
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
            world.audio.playGameOver();
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
        // this is the moment the round begins: whatever state the player is in
        // right now is what a restarted round must return to
        startPlayerX = world.player.x;
        startPlayerY = world.player.y;
        startPlayerAngle = world.player.angle;
        startPlayerVx = world.player.vx;
        startPlayerVy = world.player.vy;
        startPlayerHp = world.player.hp;
        // every round opens on an empty field: any bullets still in flight
        // from the previous round are discarded, so the round-start scene is
        // well-defined (and reproducible by a restart)
        world.bullets.clear();
        world.enemies.clear();
        killsAtRoundStart = kills;
        damageAtRoundStart = damageDealt;
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
        // every round after the first announces itself with the jingle
        if (round > 1) world.audio.playNextRound();
        log.info("Round {} started: {} enemies (hp={}, dmg={})", round, count, hp, dmg);
    }
}
