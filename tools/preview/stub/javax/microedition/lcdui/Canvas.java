package javax.microedition.lcdui;

/** Заглушка MIDP Canvas для запуска игры на JDK 8 (превью/отладка). */
public abstract class Canvas {
    public static final int UP = -1, DOWN = -2, LEFT = -3, RIGHT = -4, FIRE = -5;
    public static final int GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;
    public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50,
            KEY_NUM3 = 51, KEY_NUM4 = 52, KEY_NUM5 = 53, KEY_NUM6 = 54,
            KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57,
            KEY_STAR = 42, KEY_POUND = 35;

    private int width = 240, height = 320;

    protected Canvas() {}

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getKeyName(int k) { return ""; }
    public int getGameAction(int k) { return 0; }
    public int getKeyCode(int gameAction) { return 0; }
    public void setFullScreenMode(boolean b) {}
    public boolean isDoubleBuffered() { return true; }
    public boolean hasPointerEvents() { return false; }
    public boolean hasPointerMotionEvents() { return false; }
    public boolean hasRepeatEvents() { return false; }
    public final void repaint() {}
    public final void repaint(int x, int y, int w, int h) {}
    public final void serviceRepaints() {}
    protected void keyPressed(int k) {}
    protected void keyReleased(int k) {}
    protected void keyRepeated(int k) {}
    protected void showNotify() {}
    protected void hideNotify() {}
    protected void sizeChanged(int w, int h) {}
    protected abstract void paint(Graphics g);

    /** Инъекция клавиши из превью. */
    public void injectKey(int code, boolean down) {
        if (down) keyPressed(code); else keyReleased(code);
    }
}
