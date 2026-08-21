package org.jiafeiown.shootgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final Logger log = LogManager.getLogger(Main.class);

    private GameWorld game;

    @Override
    public void create() {
        // Route libGDX's own Gdx.app.* output into log4j2 as well.
        Gdx.app.setApplicationLogger(new GdxLog4jBridge());
        log.info("Game created (libGDX {})", com.badlogic.gdx.Version.VERSION);
        game = new GameWorld();
        game.create();
    }

    @Override
    public void render() {
        game.render();
    }

    @Override
    public void resize(int width, int height) {
        log.debug("Window resized to {}x{}", width, height);
        game.resize(width, height);
    }

    @Override
    public void dispose() {
        log.info("Disposing game");
        game.dispose();
    }
}
