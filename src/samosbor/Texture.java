package samosbor;

import javax.microedition.lcdui.Image;

/**
 * Текстура из PNG. Взято из движка Mecasting (classes.Texture, Роман Лагин)
 * и адаптировано: стены/пол 64x64 (степень двойки, 256-цветный
 * индексированный PNG), макро-снимки кожи для спрайтов 40x56 (truecolor PNG).
 * Оба варианта читаются MIDP Image.createImage(); при ошибке — solid().
 */
public final class Texture {

    public int w, h, wBits, hBits, wLen, hLen;
    public int[] rgb;
    public int[] vb;   // красно-чёрная версия (режим очков Virtual Boy)

    public static int genBits(int v) {
        int x = 0;
        while ((1 << x) < v) x++;
        return x;
    }

    public Texture(int[] pixels, int width, int height) {
        rgb = pixels;
        w = width;
        h = height;
        wBits = genBits(w);
        hBits = genBits(h);
        wLen = w - 1;
        hLen = h - 1;
        vb = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) vb[i] = toVb(rgb[i]);
    }

    /** Красный монохром: как экран Virtual Boy. */
    public static int toVb(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int l = (r * 77 + g * 150 + b * 29) >> 8;
        return 0xff000000 | (l << 16) | ((l >> 3) << 8) | (l >> 4);
    }

    public static Texture load(String path) {
        try {
            Image img = Image.createImage(path);
            int iw = img.getWidth();
            int ih = img.getHeight();
            int[] pixels = new int[iw * ih];
            img.getRGB(pixels, 0, iw, 0, 0, iw, ih);
            img = null;
            return new Texture(pixels, iw, ih);
        } catch (Exception e) {
            return solid(0xff3a2048);
        }
    }

    /** Одноцветная заглушка, если PNG не загрузился. */
    public static Texture solid(int color) {
        int[] p = new int[64 * 64];
        for (int i = 0; i < p.length; i++) p[i] = color;
        return new Texture(p, 64, 64);
    }
}
