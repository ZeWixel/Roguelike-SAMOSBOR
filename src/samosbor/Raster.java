package samosbor;

/**
 * Мини-растеризатор: процедурные спрайты монстров и предметов рисуются
 * в int[] ARGB-буфер (alpha = 0 => прозрачно). Никаких Image/GC в кадре.
 */
public final class Raster {

    public int w, h;
    public int[] p;
    public int[] vb;

    public Raster(int width, int height) {
        w = width;
        h = height;
        p = new int[w * h];
    }

    /**
     * Накладывает фактуру настоящей фотографии поверх плоских заливок:
     * яркость из макро-снимка модулирует цвет, а его хрома добавляет
     * ржавчину/патину. strength — размах модуляции (обычно 60..100).
     */
    public void photoGrain(Texture t, int strength) {
        if (t == null || t.w < 1 || t.h < 1) return;
        int[] tp = t.rgb;
        for (int i = 0; i < p.length; i++) {
            int c = p[i];
            if ((c >>> 24) == 0) continue;
            int x = i % w, y = i / w;
            int tc = tp[(y % t.h) * t.w + (x % t.w)];
            int tr = (tc >> 16) & 255, tg = (tc >> 8) & 255, tb = tc & 255;
            int lum = (tr * 77 + tg * 150 + tb * 29) >> 8;
            int f = 256 + ((lum - 128) * strength) / 128;
            int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            int r = (cr * f >> 8) + (tr - lum) / 5;
            int g = (cg * f >> 8) + (tg - lum) / 5;
            int b = (cb * f >> 8) + (tb - lum) / 5;
            p[i] = 0xff000000 |
                    ((r < 0 ? 0 : r > 255 ? 255 : r) << 16) |
                    ((g < 0 ? 0 : g > 255 ? 255 : g) << 8) |
                    (b < 0 ? 0 : b > 255 ? 255 : b);
        }
    }

    /** Вызывается один раз после того, как спрайт дорисован. */
    public void buildVb() {
        vb = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            if ((p[i] >>> 24) == 0) { vb[i] = 0; continue; }
            vb[i] = Texture.toVb(p[i]);
        }
    }

    public static int rgb(int r, int g, int b) {
        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public static int darker(int c, int amount) {
        int r = ((c >> 16) & 255) - amount;
        int g = ((c >> 8) & 255) - amount;
        int b = (c & 255) - amount;
        return rgb(r, g, b);
    }

    public static int lighter(int c, int amount) {
        int r = ((c >> 16) & 255) + amount;
        int g = ((c >> 8) & 255) + amount;
        int b = (c & 255) + amount;
        return rgb(r, g, b);
    }

    public void set(int x, int y, int c) {
        if (x >= 0 && x < w && y >= 0 && y < h) p[y * w + x] = c;
    }

    public int get(int x, int y) {
        if (x >= 0 && x < w && y >= 0 && y < h) return p[y * w + x];
        return 0;
    }

    public void rect(int x, int y, int rw, int rh, int c) {
        int x0 = x < 0 ? 0 : x;
        int y0 = y < 0 ? 0 : y;
        int x1 = x + rw > w ? w : x + rw;
        int y1 = y + rh > h ? h : y + rh;
        for (int j = y0; j < y1; j++) {
            int off = j * w;
            for (int i = x0; i < x1; i++) p[off + i] = c;
        }
    }

    public void frame(int x, int y, int rw, int rh, int c) {
        rect(x, y, rw, 1, c);
        rect(x, y + rh - 1, rw, 1, c);
        rect(x, y, 1, rh, c);
        rect(x + rw - 1, y, 1, rh, c);
    }

    public void ellipse(int cx, int cy, int rx, int ry, int c) {
        if (rx <= 0 || ry <= 0) return;
        for (int y = cy - ry; y <= cy + ry; y++) {
            for (int x = cx - rx; x <= cx + rx; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx * ry * ry + dy * dy * rx * rx <= rx * rx * ry * ry)
                    set(x, y, c);
            }
        }
    }

    public void ring(int cx, int cy, int rx, int ry, int inner, int c) {
        for (int y = cy - ry; y <= cy + ry; y++) {
            for (int x = cx - rx; x <= cx + rx; x++) {
                int dx = x - cx, dy = y - cy;
                int v = dx * dx * ry * ry + dy * dy * rx * rx;
                int total = rx * rx * ry * ry;
                if (v <= total && v >= (total * inner) / 100) set(x, y, c);
            }
        }
    }

    public void line(int x0, int y0, int x1, int y1, int c, int thick) {
        int dx = x1 - x0, dy = y1 - y0;
        int n = Math.abs(dx);
        if (Math.abs(dy) > n) n = Math.abs(dy);
        if (n == 0) n = 1;
        for (int k = 0; k <= n; k++) {
            int x = x0 + dx * k / n;
            int y = y0 + dy * k / n;
            rect(x, y, thick, thick, c);
        }
    }
}
