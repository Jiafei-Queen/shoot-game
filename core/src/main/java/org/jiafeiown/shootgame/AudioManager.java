package org.jiafeiown.shootgame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owns the game's audio: loads the sound effects and the looping pause music
 * from {@code assets/audio} at startup and exposes playback helpers for the
 * gameplay events that trigger them.
 *
 * <p>Every method is null-safe on purpose: unit tests build a {@link GameWorld}
 * headlessly without ever calling {@link #create()} (no Gdx audio backend), so
 * all playback calls become silent no-ops until the audio is actually loaded.
 */
public class AudioManager {

    private static final Logger log = LogManager.getLogger(AudioManager.class);

    private static final String AUDIO_DIR = "audio/";

    /** How long the pause music takes to fade in and out. */
    private static final float PAUSE_FADE_TIME = 0.5f;

    private Sound enemyDie;
    private Sound enemyShoot;
    private Sound gameOver;
    private Sound hitMetal;
    private Sound nextRound;
    private Music pauseLoop;
    private Sound playerShoot;

    /** Target volume of the pause music (1 = full) while fading; 0 when stopped. */
    private float pauseTarget = 0f;
    /** Current volume of the pause music; moved toward {@link #pauseTarget} each frame. */
    private float pauseVolume = 0f;

    /** Loads every audio file. Called from {@link GameWorld#create()}. */
    void create() {
        enemyDie = loadSound("enemy-die.mp3");
        enemyShoot = loadSound("enemy-shoot.mp3");
        gameOver = loadSound("gameover.mp3");
        hitMetal = loadSound("hit-metal.mp3");
        nextRound = loadSound("next-round.mp3");
        pauseLoop = Gdx.audio.newMusic(Gdx.files.internal(AUDIO_DIR + "pause.mp3"));
        pauseLoop.setLooping(true);
        playerShoot = loadSound("player-shoot.mp3");
        log.info("Audio loaded: enemy-die, enemy-shoot, gameover, hit-metal, next-round, pause (loop), player-shoot");
    }

    private static Sound loadSound(String name) {
        return Gdx.audio.newSound(Gdx.files.internal(AUDIO_DIR + name));
    }

    /** Player fired a bullet. */
    void playPlayerShoot() {
        play(playerShoot);
    }

    /** An enemy fired a bullet, at 60% volume so it sits behind the player's shots. */
    void playEnemyShoot() {
        play(enemyShoot, 0.6f);
    }

    /** An enemy was defeated. */
    void playEnemyDie() {
        play(enemyDie);
    }

    /** Two bullets shattered each other, or a bullet struck an enemy. */
    void playHitMetal() {
        play(hitMetal);
    }

    /** The settlement screen appeared (player defeat or early end). */
    void playGameOver() {
        play(gameOver);
    }

    /** A round after the first one started. */
    void playNextRound() {
        play(nextRound);
    }

    /** Starts the looping pause-menu music, fading it in from its current volume. */
    void startPauseLoop() {
        if (pauseLoop == null) return;
        if (!pauseLoop.isPlaying()) {
            pauseLoop.setVolume(pauseVolume);
            pauseLoop.play();
        }
        pauseTarget = 1f;
    }

    /** Fades the looping pause-menu music out; it stops once silent. */
    void stopPauseLoop() {
        if (pauseLoop == null) return;
        pauseTarget = 0f;
    }

    /** Advances the pause music's fade in/out. Called every frame from
     *  {@link GameWorld#render()} so the fade keeps moving while the game
     *  itself is frozen on the pause screen. */
    void update(float dt) {
        if (pauseLoop == null) return;
        if (pauseVolume < pauseTarget) {
            pauseVolume = Math.min(pauseTarget, pauseVolume + dt / PAUSE_FADE_TIME);
            pauseLoop.setVolume(pauseVolume);
        } else if (pauseVolume > pauseTarget) {
            pauseVolume = Math.max(pauseTarget, pauseVolume - dt / PAUSE_FADE_TIME);
            pauseLoop.setVolume(pauseVolume);
            if (pauseVolume <= 0f && pauseLoop.isPlaying()) pauseLoop.stop();
        }
    }

    private static void play(Sound s) {
        play(s, 1f);
    }

    private static void play(Sound s, float volume) {
        if (s != null) s.play(volume);
    }

    void dispose() {
        if (enemyDie != null) enemyDie.dispose();
        if (enemyShoot != null) enemyShoot.dispose();
        if (gameOver != null) gameOver.dispose();
        if (hitMetal != null) hitMetal.dispose();
        if (nextRound != null) nextRound.dispose();
        if (pauseLoop != null) pauseLoop.dispose();
        if (playerShoot != null) playerShoot.dispose();
        enemyDie = enemyShoot = gameOver = hitMetal = nextRound = playerShoot = null;
        pauseLoop = null;
        log.info("Audio disposed");
    }
}
