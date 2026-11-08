package samosbor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

/**
 * Экспортирует ВСЮ растровую графику игры в обычные PNG (res/gfx/).
 *
 * Запускается на том же Java-коде, что и игра, но на MIDP-заглушках JDK:
 * спрайты рисуются теми же процедурными методами (makeWeapon,
 * makeItemSprite, Monster.create), а результат сохраняется в PNG. После
 * этого графика становится «открытой»: её можно редактировать в любом
 * редакторе и просто перепаковать JAR — javac больше не нужен.
 *
 * Запуск (делает build.sh при первом билде или ./build.sh --regen-art):
 *   java -cp build/preview:res samosbor.ExportGfx res/gfx
 */
public final class ExportGfx {

    private static File out;

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "res/gfx");
        if (!out.exists() && !out.mkdirs()) {
            throw new Exception("cannot create " + out);
        }

        BufferedImage dummy = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        javax.microedition.lcdui.Graphics gfx =
                new javax.microedition.lcdui.Graphics(dummy);

        Game g = new Game(null);
        Field fRenderer = field(Game.class, "renderer");
        Field fState = field(Game.class, "state");
        Field fViewH = field(Game.class, "viewH");
        Field fH = field(Game.class, "h");
        Field fW = field(Game.class, "w");
        Field fHudH = field(Game.class, "hudH");
        Method renderFrame = method(Game.class, "renderFrame",
                new Class[] {long.class});
        Method makeWeapon = method(Game.class, "makeWeapon", new Class[0]);
        Method makeItem = method(Renderer.class, "makeItemSprite",
                new Class[] {int.class});
        Renderer renderer = (Renderer) fRenderer.get(g);
        int w = fW.getInt(g), h = fH.getInt(g), viewH = fViewH.getInt(g);

        // --- спрайты, которые раньше существовали только в байт-коде -------
        save((Raster) makeWeapon.invoke(g, new Object[0]), "weapon.png");
        for (int t = 1; t <= 6; t++) {
            save((Raster) makeItem.invoke(renderer,
                    new Object[] {new Integer(t)}), "item-" + t + ".png");
        }
        Rng rng = new Rng(0x50A40C);
        for (int i = 0; i < 3; i++) {
            save(Monster.create(rng, i).frames[0], "title-" + i + ".png");
        }
        saveFontAtlas();
        save(crosshair(), "crosshair.png");
        save(crosshair(), "crosshair-hot.png");

        // --- запускаем игру, чтобы у экранов были уровень/таймер/маршрут ---
        g.injectKey(53, true); g.injectKey(53, false);   // НОВЫЙ ЗАБЕГ
        g.injectKey(53, true); g.injectKey(53, false);   // ВОЙТИ В ЭТАЖ
        g.paint(gfx);
        field(Game.class, "showMap").setBoolean(g, false);
        field(Game.class, "aiming").setBoolean(g, false);

        // --- HUD: кадр без текста, вырезаем нижнюю панель ------------------
        BitmapFont.silent = true;
        Game.bgOnly = false;
        fState.setInt(g, 3);                              // PLAY
        renderFrame.invoke(g, new Object[] {new Long(System.currentTimeMillis())});
        int[] frame = renderer.screen;
        int hudH = fHudH.getInt(g);
        Raster hud = new Raster(w, hudH);
        for (int j = 0; j < hudH; j++) {
            for (int i = 0; i < w; i++) hud.p[j * w + i] = frame[(viewH + j) * w + i];
        }
        save(hud, "hud.png");

        // --- фоновые пластины экранов: только статичный фон, без текста
        //     и без динамических панелей (их игра дорисует поверх PNG).
        Game.bgOnly = true;
        String[] names = {
            "screen-title.png", "screen-help.png", "screen-brief.png", null,
            "screen-ending.png", "screen-dead.png", null, "screen-progress.png"
        };
        for (int st = 0; st < names.length; st++) {
            if (names[st] == null) continue;
            fState.setInt(g, st);
            renderFrame.invoke(g,
                    new Object[] {new Long(System.currentTimeMillis())});
            Raster plate = new Raster(w, h);
            for (int i = 0; i < plate.p.length; i++) plate.p[i] = frame[i];
            save(plate, names[st]);
        }
        Game.bgOnly = false;
        BitmapFont.silent = false;

        // --- полноэкранный слой: по умолчанию полностью прозрачный --------
        save(new Raster(w, h), "overlay.png");
        writeTheme();
        writeReadme();
        System.out.println("  gfx: " + out.getPath() + " (weapon, 6 items, "
                + "3 title monsters, font atlas, 2 crosshairs, HUD, "
                + "6 screens, overlay, theme.txt, README.txt)");
    }

    private static Raster crosshair() {
        // Маска 21x21: четыре штриха и центральная точка. Цвет задаётся
        // палитрой в рантайме, поэтому редактируется только форма.
        Raster r = new Raster(21, 21);
        int c = 0xffffffff;
        for (int d = -9; d <= -4; d++) {
            r.p[10 * 21 + 10 + d] = c;
            r.p[10 * 21 + 10 - d] = c;
            r.p[(10 + d) * 21 + 10] = c;
            r.p[(10 - d) * 21 + 10] = c;
        }
        for (int j = 9; j <= 11; j++) {
            for (int i = 9; i <= 11; i++) {
                boolean border = (i == 9 || i == 11 || j == 9 || j == 11);
                r.p[j * 21 + i] = border ? 0xff000000 : c;
            }
        }
        return r;
    }

    /** Атлас 16 колонок, ячейка 6x8: повторяет встроенную таблицу глифов. */
    private static void saveFontAtlas() throws Exception {
        Field fG = field(BitmapFont.class, "GLYPHS");
        Field fD = field(BitmapFont.class, "DATA");
        String glyphs = (String) fG.get(null);
        String data = (String) fD.get(null);
        int cols = BitmapFont.ATLAS_COLS;
        int cw = BitmapFont.CELL_W, ch = BitmapFont.CELL_H;
        int rows = (glyphs.length() + cols - 1) / cols;
        Raster atlas = new Raster(cols * cw, rows * ch);
        for (int idx = 0; idx < glyphs.length(); idx++) {
            int gx = (idx % cols) * cw, gy = (idx / cols) * ch;
            for (int row = 0; row < 7; row++) {
                int bits = data.charAt(idx * 7 + row) - 0x100;
                for (int col = 0; col < 5; col++) {
                    if ((bits & (1 << (4 - col))) != 0) {
                        atlas.p[(gy + row) * atlas.w + gx + col] = 0xffffffff;
                    }
                }
            }
        }
        save(atlas, "font.png");
    }

    private static void writeTheme() throws Exception {
        String text =
            "# GigaKhrushchyovka: open palette. Edit values and repack the JAR.\n" +
            "# Format KEY=0xAARRGGBB (AA=FF opaque); 0xRRGGBB also works.\n" +
            "# Lines starting with # are comments. Unknown keys are ignored.\n" +
            "WHITE=0xfff0e6d0\n" +
            "RED=0xffe05050\n" +
            "PURPLE=0xffb952c7\n" +
            "YELLOW=0xffd8cf40\n" +
            "GREEN=0xff8ec06a\n" +
            "DARK=0xff180e20\n" +
            "GREY=0xff8d8e8e\n" +
            "RED_DIM=0xff741a24\n" +
            "RED_MID=0xffb02a34\n" +
            "BLACK=0xff080406\n" +
            "BG=0xff0d0812\n" +
            "SCREEN_BG=0xff120a1a\n" +
            "MAP_BG=0xff0b0610\n" +
            "PANEL_BG=0xff140b1c\n" +
            "FOG_NORMAL=0xff2a1036\n" +
            "FOG_VB=0xff200000\n" +
            "CROSSHAIR=0xffe05050\n" +
            "CROSSHAIR_HOT=0xffd8cf40\n" +
            "# Example of the short form: alpha FF is added automatically.\n" +
            "EXAMPLE_RGB=0x123456\n";
        java.io.FileOutputStream f = new java.io.FileOutputStream(new File(out, "theme.txt"));
        f.write(text.getBytes("UTF-8"));
        f.close();
    }

    private static void writeReadme() throws Exception {
        String text =
            "GigaKhrushchyovka - open graphics pack\n" +
            "=====================================\n" +
            "Edit any PNG here, then run: python3 tools/repack.py <this-folder>\n" +
            "No JDK, no compilation, no generators - just unzip / edit / repack.\n" +
            "\n" +
            "textures/*.png in the JAR  - 16 wall/floor/ceiling textures (64x64)\n" +
            "audio/*.mid in the JAR     - music and sound effects\n" +
            "gfx/weapon.png             - first-person gun (64x54)\n" +
            "gfx/item-1..6.png          - pickup sprites (24x24)\n" +
            "gfx/title-0..2.png         - monsters on the title/ending screen\n" +
            "gfx/font.png               - 5x7 font atlas, 16 cols, 6x8 cell\n" +
            "gfx/crosshair*.png         - reticle alpha masks (21x21)\n" +
            "gfx/hud.png                - bottom HUD plate (240x56)\n" +
            "gfx/screen-*.png           - full-screen backgrounds (240x320)\n" +
            "gfx/overlay.png            - layer drawn on top of every frame\n" +
            "gfx/theme.txt              - all UI/fog colors as plain text\n" +
            "                            (KEY=0xAARRGGBB, or 0xRRGGBB)\n" +
            "gfx/source/*.png           - original photo sources for textures\n" +
            "gfx/screen-play.png        - optional: add it for a custom in-game\n" +
            "                            backdrop (same for screen-pause.png)\n" +
            "\n" +
            "Alpha = 0 means transparent. Keep the pixel size of each file.\n" +
            "Delete a PNG to fall back to the built-in procedural sprite.\n" +
            "Full manifest: RESOURCES.md in the repository.\n";
        java.io.FileOutputStream f = new java.io.FileOutputStream(new File(out, "README.txt"));
        f.write(text.getBytes("UTF-8"));
        f.close();
    }

    private static void save(Raster r, String name) throws Exception {
        BufferedImage img = new BufferedImage(r.w, r.h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, r.w, r.h, r.p, 0, r.w);
        File f = new File(out, name);
        ImageIO.write(img, "png", f);
        System.out.println("  " + f.getPath() + " (" + r.w + "x" + r.h + ")");
    }

    private static Field field(Class c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Method method(Class c, String name, Class[] args) throws Exception {
        Method m = c.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }
}
