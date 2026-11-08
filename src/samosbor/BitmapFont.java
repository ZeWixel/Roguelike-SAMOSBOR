package samosbor;

/**
 * Встроенный растровый шрифт 5x7: кириллица, латиница, цифры, знаки и пиктограммы.
 * Сгенерирован tools/gen_font.py — не зависит от системного шрифта телефона.
 * ^ = сердце, & = патрон, @ = стрелка, ~ = ключ, $ = номер.
 *
 * ОДНАКО приоритет у открытого ресурса gfx/font.png: это атлас 16 колонок,
 * ячейка 6x8, глиф 5x7 в левом верхнем углу ячейки. Непрозрачный пиксель —
 * точка глифа, прозрачный — фон. Отредактировав PNG в JAR, можно поменять
 * шрифт без перекомпиляции. Если PNG нет, работает встроенная таблица DATA.
 */
public final class BitmapFont {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    public static final int ATLAS_COLS = 16;
    public static final int CELL_W = 6;
    public static final int CELL_H = 8;

    /** Для tools/preview/ExportGfx.java: рисовать фон без текста. */
    public static boolean silent = false;

    private static boolean atlasChecked;
    private static int[] atlas;
    private static int atlasW, atlasH;

    private static void ensureAtlas() {
        if (atlasChecked) return;
        atlasChecked = true;
        int[] dim = new int[2];
        int[] px = Texture.loadPixels(Gfx.DIR + "font.png", dim);
        if (px != null && dim[0] >= ATLAS_COLS * CELL_W && dim[1] >= CELL_H) {
            atlas = px;
            atlasW = dim[0];
            atlasH = dim[1];
        }
    }

    public static boolean hasAtlas() {
        ensureAtlas();
        return atlas != null;
    }

    private static final String GLYPHS = "\u0410\u0411\u0412\u0413\u0414\u0415\u0401\u0416\u0417\u0418\u0419\u041a\u041b\u041c\u041d\u041e\u041f\u0420\u0421\u0422\u0423\u0424\u0425\u0426\u0427\u0428\u0429\u042a\u042b\u042c\u042d\u042e\u042f\u0041\u0042\u0043\u0044\u0045\u0046\u0047\u0048\u0049\u004a\u004b\u004c\u004d\u004e\u004f\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057\u0058\u0059\u005a\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037\u0038\u0039\u0020\u002e\u002c\u003a\u002d\u0021\u003f\u002f\u002b\u002a\u0028\u0029\u0025\"\u0027\u003d\u0023\u003c\u003e\u005b\u005d\u005f\u005e\u0026\u0040\u007e\u0024";

    private static final String DATA =

        "\u010e\u0111\u0111\u011f\u0111\u0111\u0111\u011e\u0110\u0110\u011e\u0111\u0111\u011e\u011e\u0111\u0111\u011e\u0111\u0111\u011e\u011f\u0110\u0110\u0110\u0110\u0110\u0110\u010e\u0111\u0111\u0111\u0111\u0111\u011b\u011f\u0110\u0110\u011e\u0110\u0110\u011f\u0115\u011f\u0110\u011e\u0110\u0110\u011f\u0111\u010a\u0104\u011f\u0104\u010a\u0111\u010f\u0101\u0101\u010e\u0101\u0101\u010f\u0111\u0111\u0112\u0114\u0118\u0111\u0111\u010a\u0111\u0111\u0112\u0114\u0118\u0111\u0111\u0112\u0114\u0118\u0114\u0112\u0111\u0106\u0109\u0109\u0109\u0109\u0109\u0111\u0111\u011b\u0115\u0115\u0111\u0111\u0111\u0111\u0111\u0111\u011f\u0111\u0111\u0111\u010e\u0111\u0111\u0111\u0111\u0111\u010e\u011f\u0111\u0111\u0111\u0111\u0111\u0111\u011e\u0111\u0111\u011e\u0110\u0110\u0110\u010f\u0110\u0110\u0110\u0110\u0110\u010f\u011f\u0104\u0104\u0104\u0104\u0104\u0104\u0111\u0111\u0111\u010e\u0101\u0108\u0110\u0104\u010e\u0115" +

        "\u0115\u0115\u010e\u0104\u0111\u0111\u010a\u0104\u010a\u0111\u0111\u0111\u0111\u0111\u0111\u0111\u0111\u011e\u0111\u0111\u0111\u010f\u0101\u0101\u0101\u0115\u0115\u0115\u0115\u0115\u0115\u011f\u0115\u0115\u0115\u0115\u0115\u0115\u011d\u011e\u0110\u0110\u011e\u0111\u0111\u011e\u0111\u0111\u0111\u011e\u0111\u0111\u011e\u0110\u0110\u0110\u011e\u0111\u0111\u011e\u010e\u0111\u0101\u0106\u0101\u0111\u010e\u0110\u0117\u0119\u0119\u0119\u0117\u0110\u010f\u0111\u0111\u010f\u0102\u0104\u0110\u010e\u0111\u0111\u011f\u0111\u0111\u0111\u011e\u0111\u0111\u011e\u0111\u0111\u011e\u010f\u0110\u0110\u0110\u0110\u0110\u010f\u011e\u0111\u0111\u0111\u0111\u0111\u011e\u011f\u0110\u0110\u011e\u0110\u0110\u011f\u011f\u0110\u0110\u011e\u0110\u0110\u0110\u010f\u0110\u0110\u0113\u0111\u0111\u010e\u0111\u0111\u0111\u011f\u0111\u0111\u0111\u011f\u0104\u0104\u0104\u0104\u0104\u011f\u0107\u0102\u0102\u0102\u0102\u0112" +

        "\u010c\u0111\u0112\u0114\u0118\u0114\u0112\u0111\u0110\u0110\u0110\u0110\u0110\u0110\u011f\u0111\u011b\u0115\u0115\u0111\u0111\u0111\u0111\u0119\u0115\u0113\u0111\u0111\u0111\u010e\u0111\u0111\u0111\u0111\u0111\u010e\u011e\u0111\u0111\u011e\u0110\u0110\u0110\u010e\u0111\u0111\u0111\u0115\u0112\u010d\u011e\u0111\u0111\u011e\u0114\u0112\u0111\u010f\u0110\u0110\u010e\u0101\u0101\u011e\u011f\u0104\u0104\u0104\u0104\u0104\u0104\u0111\u0111\u0111\u0111\u0111\u0111\u010e\u0111\u0111\u0111\u0111\u0111\u010a\u0104\u0111\u0111\u0111\u0115\u0115\u011b\u0111\u0111\u0111\u010a\u0104\u010a\u0111\u0111\u0111\u0111\u010a\u0104\u0104\u0104\u0104\u011f\u0101\u0102\u0104\u0108\u0110\u011f\u010e\u0111\u0113\u0115\u0119\u0111\u010e\u0104\u010c\u0104\u0104\u0104\u0104\u011f\u010e\u0111\u0101\u0102\u0104\u0108\u011f\u011e\u0101\u0101\u010e\u0101\u0101\u011e\u0102\u0106\u010a\u0112\u011f\u0102\u0102\u011f\u0110" +

        "\u011e\u0101\u0101\u0111\u010e\u0106\u0108\u0110\u011e\u0111\u0111\u010e\u011f\u0101\u0102\u0104\u0108\u0108\u0108\u010e\u0111\u0111\u010e\u0111\u0111\u010e\u010e\u0111\u0111\u010f\u0101\u0102\u010c\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u010c\u010c\u0100\u0100\u0100\u0100\u010c\u010c\u0108\u0100\u010c\u010c\u0100\u010c\u010c\u0100\u0100\u0100\u0100\u011f\u0100\u0100\u0100\u0104\u0104\u0104\u0104\u0104\u0100\u0104\u010e\u0111\u0101\u0102\u0104\u0100\u0104\u0101\u0101\u0102\u0104\u0108\u0110\u0110\u0100\u0104\u0104\u011f\u0104\u0104\u0100\u0100\u0115\u010e\u011f\u010e\u0115\u0100\u0102\u0104\u0108\u0108\u0108\u0104\u0102\u0108\u0104\u0102\u0102\u0102\u0104\u0108\u0119\u0119\u0102\u0104\u0108\u0113\u0113\u010a\u010a\u0100\u0100\u0100\u0100\u0100\u0104\u0104\u0100\u0100\u0100\u0100\u0100\u0100\u0100\u011f\u0100\u011f\u0100\u0100\u010a\u010a\u011f\u010a\u011f" +

        "\u010a\u010a\u0102\u0104\u0108\u0110\u0108\u0104\u0102\u0108\u0104\u0102\u0101\u0102\u0104\u0108\u010e\u0108\u0108\u0108\u0108\u0108\u010e\u010e\u0102\u0102\u0102\u0102\u0102\u010e\u0100\u0100\u0100\u0100\u0100\u0100\u011f\u010a\u011f\u011f\u011f\u010e\u010e\u0104\u0104\u010e\u010e\u010e\u010e\u010e\u0104\u0100\u0104\u0107\u011f\u0107\u0104\u0100\u0107\u0105\u0107\u0104\u0107\u0104\u0104\u010a\u011f\u010a\u011f\u010a\u0100\u0100";

    private BitmapFont() {}

    /** Ширина строки в пикселях при масштабе scale. */
    public static int width(String s, int scale) {
        return s.length() * 6 * scale;
    }

    public static char upper(char c) {
        if (c >= 'a' && c <= 'z') return (char) (c - 32);
        if (c >= '\u0430' && c <= '\u044f') return (char) (c - 32);
        if (c == '\u0451') return '\u0401';
        return c;
    }

    /** Одна точка глифа с масштабированием и отсечением по краям экрана. */
    private static void pixel(int[] buf, int sw, int sh, int px, int py,
                              int color, int scale) {
        for (int sy = 0; sy < scale; sy++) {
            int yy = py + sy;
            if (yy < 0 || yy >= sh) continue;
            int off = yy * sw;
            for (int sx = 0; sx < scale; sx++) {
                int xx = px + sx;
                if (xx >= 0 && xx < sw) buf[off + xx] = color;
            }
        }
    }

    /** Рисует строку в кадровый буфер. Цвет — ARGB (0xffRRGGBB). */
    public static void draw(int[] buf, int sw, int sh, String s,
                            int x, int y, int color, int scale) {
        if (silent) return;
        ensureAtlas();
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = upper(s.charAt(i));
            if (ch != ' ') {
                int idx = GLYPHS.indexOf(ch);
                if (idx < 0) idx = GLYPHS.indexOf('?');
                if (atlas != null) {
                    // Открытый атлас: пользователь может рисовать в ячейке 6x8
                    // любые точки, включая декоративные выносные элементы.
                    int gx = (idx % ATLAS_COLS) * CELL_W;
                    int gy = (idx / ATLAS_COLS) * CELL_H;
                    for (int r = 0; r < CELL_H; r++) {
                        int ay = gy + r;
                        if (ay >= atlasH) break;
                        for (int c = 0; c < CELL_W; c++) {
                            int ax = gx + c;
                            if (ax >= atlasW) break;
                            if ((atlas[ay * atlasW + ax] >>> 24) == 0) continue;
                            pixel(buf, sw, sh, cx + c * scale, y + r * scale,
                                    color, scale);
                        }
                    }
                } else {
                    int base = idx * 7;
                    for (int r = 0; r < 7; r++) {
                        int bits = DATA.charAt(base + r) - 0x100;
                        for (int c = 0; c < 5; c++) {
                            if ((bits & (1 << (4 - c))) == 0) continue;
                            pixel(buf, sw, sh, cx + c * scale, y + r * scale,
                                    color, scale);
                        }
                    }
                }
            }
            cx += 6 * scale;
            if (cx >= sw) break;
        }
    }

    /** Рисует строку с тёмным контуром (для читаемости поверх 3D). */
    public static void drawShadow(int[] buf, int sw, int sh, String s,
                                  int x, int y, int color, int scale) {
        draw(buf, sw, sh, s, x - 1, y, 0xff000000, scale);
        draw(buf, sw, sh, s, x + 1, y, 0xff000000, scale);
        draw(buf, sw, sh, s, x, y - 1, 0xff000000, scale);
        draw(buf, sw, sh, s, x, y + 1, 0xff000000, scale);
        draw(buf, sw, sh, s, x, y, color, scale);
    }
}
