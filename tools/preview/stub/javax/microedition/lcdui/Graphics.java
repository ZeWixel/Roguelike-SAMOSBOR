package javax.microedition.lcdui;

import java.awt.image.BufferedImage;

/** Заглушка Graphics: только drawRGB (вся игра рисует в int[] буфер). */
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8,
            TOP = 16, BOTTOM = 32, BASELINE = 64;

    public final BufferedImage target;
    private int color = 0xff000000;

    public Graphics(BufferedImage target) { this.target = target; }

    public void drawRGB(int[] rgb, int offset, int scanlength, int x, int y,
                        int width, int height, boolean processAlpha) {
        int iw = target.getWidth(), ih = target.getHeight();
        for (int j = 0; j < height; j++) {
            int yy = y + j;
            if (yy < 0 || yy >= ih) continue;
            for (int i = 0; i < width; i++) {
                int xx = x + i;
                if (xx < 0 || xx >= iw) continue;
                int c = rgb[offset + j * scanlength + i];
                if (!processAlpha) c |= 0xff000000;
                target.setRGB(xx, yy, c);
            }
        }
    }

    public void setColor(int rgb) { color = 0xff000000 | rgb; }
    public void setColor(int r, int g, int b) { color = 0xff000000 | (r << 16) | (g << 8) | b; }
    public void fillRect(int x, int y, int w, int h) {
        java.awt.Graphics2D g2 = target.createGraphics();
        g2.setColor(new java.awt.Color(color, true));
        g2.fillRect(x, y, w, h);
        g2.dispose();
    }
    public void drawLine(int x1, int y1, int x2, int y2) {
        java.awt.Graphics2D g2 = target.createGraphics();
        g2.setColor(new java.awt.Color(color, true));
        g2.drawLine(x1, y1, x2, y2);
        g2.dispose();
    }
    public void drawString(String s, int x, int y, int anchor) {
        java.awt.Graphics2D g2 = target.createGraphics();
        g2.setColor(new java.awt.Color(color, true));
        g2.drawString(s, x, y + 10);
        g2.dispose();
    }
    public void drawRect(int x, int y, int w, int h) {
        java.awt.Graphics2D g2 = target.createGraphics();
        g2.setColor(new java.awt.Color(color, true));
        g2.drawRect(x, y, w, h);
        g2.dispose();
    }
}
