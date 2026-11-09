package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    private static final Display INSTANCE = new Display();
    public static Display getDisplay(MIDlet m) { return INSTANCE; }
    public void setCurrent(Canvas c) {}
    public boolean vibrate(int ms) { return true; }
    public boolean flashBacklight(int ms) { return true; }
    public int numColors() { return 65536; }
}
