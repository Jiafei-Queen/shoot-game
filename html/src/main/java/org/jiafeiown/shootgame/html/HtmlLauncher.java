package org.jiafeiown.shootgame.html;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;

import org.jiafeiown.shootgame.Main;

/** Launches the game in the browser (GWT / WebGL). */
public class HtmlLauncher extends GwtApplication {

    @Override
    public GwtApplicationConfiguration getConfig() {
        // resizable application: canvas follows the browser window (minus a
        // small padding), and window resizes are forwarded to the game's
        // resize() -> FitViewport, keeping the 800x1280 world scaled and
        // letterboxed exactly like the desktop build
        GwtApplicationConfiguration config = new GwtApplicationConfiguration();
        config.useAccelerometer = false;
        config.useGyroscope = false;
        return config;
    }

    @Override
    public ApplicationListener createApplicationListener() {
        return new Main();
    }
}
