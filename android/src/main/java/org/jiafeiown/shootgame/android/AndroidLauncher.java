package org.jiafeiown.shootgame.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import org.jiafeiown.shootgame.Main;

/** Launches the game on Android phones and tablets. */
public class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        // the game doesn't use the accelerometer/compass
        config.useAccelerometer = false;
        config.useCompass = false;
        initialize(new Main(), config);
    }
}
