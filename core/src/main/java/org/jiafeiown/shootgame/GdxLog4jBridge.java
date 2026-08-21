package org.jiafeiown.shootgame;

import com.badlogic.gdx.ApplicationLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Routes every {@code Gdx.app.*} log call (used by libGDX internals and most
 * gdx add-ons) into Log4j2, so the whole game logs through a single pipeline.
 *
 * <p>Install once at startup: {@code Gdx.app.setApplicationLogger(new GdxLog4jBridge());}
 */
public final class GdxLog4jBridge implements ApplicationLogger {
    private static final Logger log = LogManager.getLogger("gdx");

    @Override
    public void log(String tag, String message) {
        log.info("[{}] {}", tag, message);
    }

    @Override
    public void log(String tag, String message, Throwable exception) {
        log.info("[{}] {}", tag, message, exception);
    }

    @Override
    public void error(String tag, String message) {
        log.error("[{}] {}", tag, message);
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        log.error("[{}] {}", tag, message, exception);
    }

    @Override
    public void debug(String tag, String message) {
        log.debug("[{}] {}", tag, message);
    }

    @Override
    public void debug(String tag, String message, Throwable exception) {
        log.debug("[{}] {}", tag, message, exception);
    }
}
