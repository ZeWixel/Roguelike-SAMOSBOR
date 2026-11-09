package classes;

import classes.game.Game;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/**
 *
 * @author Roman Lahin
 */
public class Main extends MIDlet {
    
    public boolean started = false;
    public Canvas gameScreen;
    
    protected void startApp() {
        if(!started) {
            started=true;
            gameScreen = new Game(this);
            Display.getDisplay(this).setCurrent(gameScreen);
        }
    }
    
    protected void destroyApp(boolean unconditional) {
    }

    protected void pauseApp() {}

    
}
