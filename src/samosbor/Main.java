package samosbor;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** Точка входа MIDlet'а. */
public class Main extends MIDlet {

    private boolean started;

    protected void startApp() {
        if (!started) {
            started = true;
            Display.getDisplay(this).setCurrent(new Game(this));
        }
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
