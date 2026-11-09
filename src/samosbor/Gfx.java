package samosbor;

import java.io.InputStream;

/**
 * Открытые ресурсы игры.
 *
 * Вся растровая графика лежит в JAR обычными PNG в каталоге gfx/, палитра —
 * простым текстом в gfx/theme.txt. Чтобы изменить любой спрайт, достаточно
 * распаковать JAR, отредактировать PNG и запаковать его обратно
 * (tools/repack.py делает это одной командой); перекомпилировать Java не нужно.
 *
 * Каждый ресурс необязателен: если файл удалён или битый, соответствующий
 * спрайт рисуется старым процедурным кодом. Это сохраняет совместимость
 * со старыми JAR и позволяет редактировать ресурсы по одному.
 *
 * Память CLDC 1.1: полноэкранные пластины грузятся лениво и в памяти живёт
 * только ОДНА (та, что сейчас на экране), к тому же без второго массива vb.
 * Полностью прозрачный overlay вообще выбрасывается после проверки.
 *
 * Только CLDC 1.1 / MIDP 2.0: Image.createImage(), массивы, InputStream.
 */
public final class Gfx {

    public static final String DIR = "/gfx/";

    /** Имена фоновых пластин по номеру состояния Game. */
    private static final String[] SCREEN = {
        "title", "help", "brief", "play", "ending", "dead", "pause", "progress"
    };

    private static boolean loaded;
    private static Raster weapon, hud, crosshair, crosshairHot;
    private static final Raster[] items = new Raster[7];
    private static final Raster[] title = new Raster[3];

    private static int[] overlay;
    private static int overlayW, overlayH;
    private static boolean overlayVisible;

    private static int plateState = -99;
    private static int[] plate;
    private static int plateW, plateH;

    // Отдельные части модульных монстров: лениво нарезаются из открытых
    // полосок gfx/parts/{kind}-{palette}.png. Большой int[] атласа сразу
    // освобождается, в живых остаются только маленькие обрезанные Raster'ы.
    private static final Raster[][][] partCache =
            new Raster[4][MonsterRig.PARTS][MonsterRig.VARIANTS];
    private static final int[][][] partBaseX =
            new int[4][MonsterRig.PARTS][MonsterRig.VARIANTS];
    private static final int[][][] partBaseY =
            new int[4][MonsterRig.PARTS][MonsterRig.VARIANTS];
    private static final boolean[][] stripLoaded =
            new boolean[4][MonsterRig.PARTS];

    private static final String[] themeKeys = new String[32];
    private static final int[] themeValues = new int[32];
    private static int themeCount;
    private static int parsedHex;

    /** Текущий экран для фоновой пластины; -1 = пластину не применять. */
    private static int screen = -1;

    private Gfx() {}

    /** Небольшой спрайт: PNG -> Raster с готовой красно-чёрной копией vb. */
    private static Raster pngRaster(String name) {
        int[] dim = new int[2];
        int[] px = Texture.loadPixels(DIR + name, dim);
        if (px == null) return null;
        Raster r = new Raster(dim[0], dim[1]);
        for (int i = 0; i < r.p.length && i < px.length; i++) r.p[i] = px[i];
        r.buildVb();
        return r;
    }

    /** Большой полноэкранный слой: только int[], без дублирования в vb. */
    private static int[] pngPixels(String name, int[] dim) {
        return Texture.loadPixels(DIR + name, dim);
    }

    public static synchronized void init() {
        if (loaded) return;
        loaded = true;
        weapon = pngRaster("weapon.png");
        hud = pngRaster("hud.png");
        crosshair = pngRaster("crosshair.png");
        crosshairHot = pngRaster("crosshair-hot.png");
        for (int i = 1; i < items.length; i++) items[i] = pngRaster("item-" + i + ".png");
        for (int i = 0; i < title.length; i++) title[i] = pngRaster("title-" + i + ".png");

        int[] dim = new int[2];
        int[] ov = pngPixels("overlay.png", dim);
        if (ov != null) {
            boolean any = false;
            for (int i = 0; i < ov.length; i++) {
                if ((ov[i] >>> 24) != 0) { any = true; break; }
            }
            // Полностью прозрачный слой не держим в куче вообще.
            if (any) {
                overlay = ov;
                overlayW = dim[0];
                overlayH = dim[1];
                overlayVisible = true;
            }
        }
        loadTheme();
    }

    public static Raster weapon() { init(); return weapon; }
    public static Raster hud() { init(); return hud; }
    public static Raster crosshair(boolean hot) {
        init();
        return hot && crosshairHot != null ? crosshairHot : crosshair;
    }
    public static Raster item(int type) {
        init();
        return (type > 0 && type < items.length) ? items[type] : null;
    }
    public static Raster titleMonster(int i) {
        init();
        return (i >= 0 && i < title.length) ? title[i] : null;
    }

    /** Цвет из gfx/theme.txt; если ключа нет — значение по умолчанию из кода. */
    public static int color(String key, int fallback) {
        init();
        for (int i = 0; i < themeCount; i++) {
            if (themeKeys[i] != null && themeKeys[i].equals(key)) return themeValues[i];
        }
        return fallback;
    }

    // --------------------------------------------------- модульные монстры

    private static boolean badPart(int palette, int kind, int variant) {
        return palette < 0 || palette > 3 || kind < 0 || kind >= MonsterRig.PARTS ||
               variant < 0 || variant >= MonsterRig.VARIANTS;
    }

    /** Один отдельный графический элемент твари (или null, если детали нет). */
    public static synchronized Raster part(int palette, int kind, int variant) {
        if (badPart(palette, kind, variant)) return null;
        ensureStrip(palette, kind);
        return partCache[palette][kind][variant];
    }

    public static synchronized int partX(int palette, int kind, int variant) {
        if (badPart(palette, kind, variant)) return 0;
        ensureStrip(palette, kind);
        return partBaseX[palette][kind][variant];
    }

    public static synchronized int partY(int palette, int kind, int variant) {
        if (badPart(palette, kind, variant)) return 0;
        ensureStrip(palette, kind);
        return partBaseY[palette][kind][variant];
    }

    /** Полоса 240x56 = шесть вариантов одной части одной палитры. */
    private static void ensureStrip(int palette, int kind) {
        init();
        if (stripLoaded[palette][kind]) return;
        stripLoaded[palette][kind] = true;
        int[] dim = new int[2];
        int[] px = Texture.loadPixels(
                DIR + "parts/" + MonsterRig.KIND[kind] + "-" + palette + ".png", dim);
        if (px == null || dim[0] < MonsterRig.VARIANTS * MonsterRig.CELL_W ||
                dim[1] < MonsterRig.CELL_H) return;
        int aw = dim[0], ah = dim[1];
        for (int v = 0; v < MonsterRig.VARIANTS; v++) {
            int x0 = v * MonsterRig.CELL_W;
            int minX = MonsterRig.CELL_W, minY = MonsterRig.CELL_H, maxX = -1, maxY = -1;
            for (int y = 0; y < MonsterRig.CELL_H; y++) {
                for (int x = 0; x < MonsterRig.CELL_W; x++) {
                    if ((px[y * aw + x0 + x] >>> 24) == 0) continue;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
            if (maxX < 0) continue;               // вариант без этой детали
            Raster r = new Raster(maxX - minX + 1, maxY - minY + 1);
            for (int j = 0; j < r.h; j++) {
                for (int i = 0; i < r.w; i++) {
                    r.p[j * r.w + i] = px[(minY + j) * aw + x0 + minX + i];
                }
            }
            r.buildVb();
            partCache[palette][kind][v] = r;
            partBaseX[palette][kind][v] = minX;
            partBaseY[palette][kind][v] = minY;
        }
        // px больше не нужен: сборщик освободит ~54 КБ сразу.
    }

    public static void setScreen(int state) { screen = state; }

    /** Ленивая загрузка пластины именно текущего экрана (одна в памяти). */
    private static synchronized void ensurePlate(int state) {
        init();
        if (plateState == state) return;
        plateState = state;
        plate = null;
        if (state < 0 || state >= SCREEN.length) return;
        int[] dim = new int[2];
        plate = pngPixels("screen-" + SCREEN[state] + ".png", dim);
        plateW = dim[0];
        plateH = dim[1];
    }

    public static boolean hasPlate(int state) {
        ensurePlate(state);
        return plate != null;
    }

    /**
     * Кладёт фоновую пластину текущего экрана в кадровый буфер.
     * Возвращает true, если PNG нашёлся — тогда процедурную заливку делать не надо.
     */
    public static boolean plate(int[] buf, int w, int h) {
        if (screen < 0 || screen >= SCREEN.length) return false;
        ensurePlate(screen);
        if (plate == null || plateW != w || plateH != h) return false;
        int n = buf.length < plate.length ? buf.length : plate.length;
        for (int i = 0; i < n; i++) {
            int c = plate[i];
            if ((c >>> 24) != 0) buf[i] = c;
        }
        return true;
    }

    /**
     * Необязательный полноэкранный слой поверх кадра: CRT-маска, виньетка,
     * рамка, логотип. По умолчанию полностью прозрачный и в куче не живёт.
     */
    public static void drawOverlay(int[] buf, int w, int h) {
        init();
        if (!overlayVisible || overlayW != w || overlayH != h) return;
        int n = buf.length < overlay.length ? buf.length : overlay.length;
        for (int i = 0; i < n; i++) {
            int c = overlay[i];
            int a = c >>> 24;
            if (a == 0) continue;
            if (a == 255) { buf[i] = c; continue; }
            int d = buf[i], ia = 255 - a;
            int r = (((c >> 16) & 255) * a + ((d >> 16) & 255) * ia) / 255;
            int g = (((c >> 8) & 255) * a + ((d >> 8) & 255) * ia) / 255;
            int b = ((c & 255) * a + (d & 255) * ia) / 255;
            buf[i] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
    }

    // ------------------------------------------------------------- палитра

    /** Читает строки вида KEY=0xAARRGGBB (или 0xRRGGBB) из gfx/theme.txt. */
    private static void loadTheme() {
        InputStream in = null;
        try {
            in = Gfx.class.getResourceAsStream(DIR + "theme.txt");
            if (in == null) return;
            byte[] data = new byte[8192];
            int len = 0, b;
            while (len < data.length && (b = in.read()) >= 0) data[len++] = (byte) b;
            int pos = 0;
            while (pos < len && themeCount < themeKeys.length) {
                int eol = pos;
                while (eol < len && data[eol] != '\n' && data[eol] != '\r') eol++;
                if (eol > pos && data[pos] != '#') {
                    int eq = -1;
                    for (int j = pos; j < eol; j++) {
                        if (data[j] == '=') { eq = j; break; }
                    }
                    if (eq > pos && eol > eq + 1) {
                        String key = new String(data, pos, eq - pos).trim();
                        String val = new String(data, eq + 1, eol - eq - 1).trim();
                        if (val.startsWith("0x") || val.startsWith("0X")) {
                            val = val.substring(2);
                        }
                        if (key.length() > 0 && val.length() > 0) {
                            if (parseHex(val)) {
                                // 8 цифр 0xAARRGGBB дают отрицательный int —
                                // это нормальный ARGB, а не ошибка разбора.
                                int argb = parsedHex;
                                if (val.length() <= 6) argb |= 0xff000000;
                                themeKeys[themeCount] = key;
                                themeValues[themeCount] = argb;
                                themeCount++;
                            }
                        }
                    }
                }
                pos = eol + 1;
                while (pos < len && (data[pos] == '\n' || data[pos] == '\r')) pos++;
            }
        } catch (Throwable t) {
            // Нет файла / нет доступа — работаем со встроенной палитрой.
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable t) {}
            }
        }
    }

    /** Свой hex-парсер: не зависит от реализаций Long/Integer на старых KVM. */
    private static boolean parseHex(String s) {
        int v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d;
            if (c >= '0' && c <= '9') d = c - '0';
            else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
            else if (c >= 'A' && c <= 'F') d = c - 'A' + 10;
            else return false;
            v = (v << 4) | d;
        }
        parsedHex = v;
        return true;
    }
}
