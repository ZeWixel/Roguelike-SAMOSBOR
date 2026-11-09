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
    /** Для SelfTest: эмулятор, который не знает НИ одного вендорского кода. */
    public static boolean rejectAllVendorNames = false;

    /**
     * Эмулируем J2ME Loader: на неизвестный keycode он бросает
     * IllegalArgumentException("unknown keycode -9"), а не возвращает "".
     * Это позволяет SelfTest ловить реальный класс падений в detectSoftKeys().
     */
    public String getKeyName(int k) {
        if (k < 0 && rejectAllVendorNames) {
            throw new IllegalArgumentException("unknown keycode " + k);
        }
        switch (k) {
            case -1: return "UP";
            case -2: return "DOWN";
            case -3: return "LEFT";
            case -4: return "RIGHT";
            case -5: return "FIRE";
            case -6: return "SOFT LEFT";
            case -7: return "SOFT RIGHT";
            case -10: return "SOFT RIGHT";
            case -11: return "SOFT RIGHT";
            case -22: return "SOFT RIGHT";
            case 42: return "*";
            case 35: return "#";
            case 48: return "0";
            case 49: return "1";
            case 50: return "2";
            case 51: return "3";
            case 52: return "4";
            case 53: return "5";
            case 54: return "6";
            case 55: return "7";
            case 56: return "8";
            case 57: return "9";
            default:
                throw new IllegalArgumentException("unknown keycode " + k);
        }
    }
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
