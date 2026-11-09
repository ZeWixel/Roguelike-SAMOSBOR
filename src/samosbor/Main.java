package samosbor;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** Точка входа MIDlet'а. */
public class Main extends MIDlet {

    private boolean started;
    private Game game;

    protected void startApp() {
        if (!started) {
            started = true;
            game = new Game(this);
            Display.getDisplay(this).setCurrent(game);
        }
    }

    protected void pauseApp() {
        if (game != null) game.onPause();
    }

    protected void destroyApp(boolean unconditional) {
        if (game != null) game.onPause();
    }
}
