package samosbor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * ПРЕВЬЮ: запускает тот же самый J2ME-код на JDK 8 через заглушки MIDP/MMAPI/RMS.
 * 1) игровой цикл рисует в BufferedImage;
 * 2) HttpServer отдаёт кадры как PNG, события звука и принимает нажатия клавиш;
 * 3) браузер показывает живой экран 240x320 с управлением с клавиатуры.
 * Это не порт на JS — в браузере играет скомпилированный samosbor.Game.
 *
 * MIDI на JDK-заглушках не звучит: /events дублирует эффекты через WebAudio.
 *
 * Запуск: java -cp build/preview:res samosbor.Preview [порт]
 *         java -cp build/preview:res samosbor.Preview --capture dir
 */
public final class Preview {

    private static final int W = 240, H = 320;
    private static final BufferedImage frame =
            new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
    private static final Graphics gfx = new Graphics(frame);
    private static volatile Game game;

    private Preview() {}

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--capture".equals(args[0])) {
            capture(args[1]);
            return;
        }
        int port = 8080;
        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        game = new Game(null);
        startLoop();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new HttpHandler() {
            public void handle(HttpExchange ex) throws java.io.IOException {
                String p = ex.getRequestURI().getPath();
                if ("/frame.png".equals(p)) { sendPng(ex); return; }
                if ("/key".equals(p)) { handleKey(ex); return; }
                if ("/events".equals(p)) { sendEvents(ex); return; }
                if ("/reset".equals(p)) {
                    synchronized (Preview.class) { game = new Game(null); }
                    send(ex, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                byte[] html = readHtml();
                send(ex, "text/html; charset=utf-8", html);
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Preview: http://0.0.0.0:" + port + " (J2ME Game on MIDP stubs)");
    }

    private static void startLoop() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    long t0 = System.currentTimeMillis();
                    Game g = game;
                    synchronized (g) {
                        g.paint(gfx);
                    }
                    long spent = System.currentTimeMillis() - t0;
                    long sleep = 40 - spent;
                    if (sleep < 1) sleep = 1;
                    try { Thread.sleep(sleep); } catch (Exception ignored) {}
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static byte[] readHtml() {
        try {
            return Files.readAllBytes(Paths.get("tools/preview/web/index.html"));
        } catch (Exception e) {
            return ("<html><body><h1>index.html not found</h1></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange ex, String type, byte[] data)
            throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, data.length);
        OutputStream os = ex.getResponseBody();
        os.write(data);
        os.close();
    }

    /** Имена MIDI-эффектов для браузерной озвучки WebAudio. */
    private static void sendEvents(HttpExchange ex) throws java.io.IOException {
        String e = Audio.lastEvent == null ? "" : Audio.lastEvent;
        String m = Audio.lastMusic == null ? "" : Audio.lastMusic;
        String json = "{\"event\":\"" + e + "\",\"at\":" + Audio.lastEventAt +
                ",\"music\":\"" + m + "\"}";
        send(ex, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendPng(HttpExchange ex) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Game g = game;
        synchronized (g) {
            try { ImageIO.write(frame, "png", out); } catch (Exception e) { e.printStackTrace(); }
        }
        byte[] data = out.toByteArray();
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, data.length);
        OutputStream os = ex.getResponseBody();
        os.write(data);
        os.close();
    }

    private static void handleKey(HttpExchange ex) throws java.io.IOException {
        String q = ex.getRequestURI().getQuery();
        int code = 0;
        boolean down = true;
        if (q != null) {
            String[] parts = q.split("&");
            for (String p : parts) {
                if (p.startsWith("k=")) {
                    try { code = Integer.parseInt(p.substring(2)); } catch (Exception ignored) {}
                } else if (p.startsWith("d=")) {
                    down = !"0".equals(p.substring(2));
                }
            }
        }
        Game g = game;
        // 99 = отладочный показ карты продвижения (только в превью).
        if (code == 99 && down) {
            synchronized (g) {
                try { forceProgress(g); } catch (Exception e) { e.printStackTrace(); }
            }
            send(ex, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
            return;
        }
        synchronized (g) { g.injectKey(code, down); }
        send(ex, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------- режим скриншотов

    private static void capture(String dir) throws Exception {
        File d = new File(dir);
        d.mkdirs();
        Save.clear();
        // Кадры в docs/ должны быть воспроизводимыми: иначе seed забега
        // берётся из currentTimeMillis() и «вид из коридора» случайно
        // оказывается тупиком. Seed можно переопределить свойством.
        int seed = 0x1a2b313;  // этаж с широким проходом и тварью вдалеке
        String s = System.getProperty("samosbor.capture.seed");
        if (s != null) {
            try { seed = Integer.decode(s).intValue(); } catch (Exception ignored) {}
        }
        Game.demoSeed = seed;
        Game g = new Game(null);
        BufferedImage img = frame;
        Graphics g2 = gfx;
        g.paint(g2);
        save(img, new File(d, "01-title.png"));           // главное меню

        tap(g, 56); tap(g, 56); g.paint(g2);              // классическое перелистывание
        save(img, new File(d, "02-menu-nav.png"));
        tap(g, 50); tap(g, 50);                           // вернулись к «НОВЫЙ ЗАБЕГ»

        tap(g, 53); g.paint(g2);                          // НОВЫЙ ЗАБЕГ
        save(img, new File(d, "03-brief.png"));
        tap(g, 53); g.paint(g2);                          // НАЧАТЬ
        save(img, new File(d, "04-play.png"));            // вид из коридора

        tap(g, 55); g.paint(g2);                          // 7 — достать оружие
        save(img, new File(d, "05-aiming.png"));          // прицел + FP-оружие

        int shotMonster = placeMonsterInFront(g);
        g.paint(g2);
        save(img, new File(d, "06-monster.png"));         // модульная тварь в прицеле

        tap(g, 53);                                      // один выстрел = смерть
        // Сдвигаем начало разлёта в прошлое, чтобы на кадре были ВИДНЫ
        // отдельные фрагменты, а не только первый кадр разрушения.
        Field fLevel2 = Game.class.getDeclaredField("level");
        fLevel2.setAccessible(true);
        Level shotLevel = (Level) fLevel2.get(g);
        Monster shot = shotLevel.monsters[shotMonster];
        long deathNow = System.currentTimeMillis();
        shot.deathStart = deathNow - 420;
        shot.deathUntil = shot.deathStart + MonsterRig.DEATH_MS;
        g.paint(g2);
        save(img, new File(d, "07-shot.png"));             // разлёт девяти модулей

        tap(g, 35); g.paint(g2);                          // # = карта
        save(img, new File(d, "08-map.png"));
        tap(g, 35); g.paint(g2);                          // закрыть карту

        tap(g, 42); g.paint(g2);                          // * = очки Virtual Boy
        save(img, new File(d, "09-virtualboy.png"));
        tap(g, 42);                                       // снять очки VB

        forceBreach(g);
        g.paint(g2);
        save(img, new File(d, "10-breach.png"));           // тварь пробила стену

        tap(g, -7); g.paint(g2);                          // правая софт = пауза
        save(img, new File(d, "11-pause.png"));
        tap(g, -7);                                       // снять паузу

        if (forceDoor(g)) {
            g.paint(g2);
            save(img, new File(d, "13-door.png"));         // дверь квартиры
            enterApartment(g);
            g.paint(g2);
            save(img, new File(d, "14-apartment.png"));    // внутри квартиры
        }

        forceProgress(g);
        g.paint(g2);
        save(img, new File(d, "12-progress.png"));         // карта продвижения
        Save.clear();
        System.out.println("capture done -> " + dir);
    }

    private static int demoDoorX = -1, demoDoorY = -1;

    private static int degOf(int vx, int vy) {
        return vx < 0 ? 0 : (vx > 0 ? 180 : (vy < 0 ? 90 : 270));
    }

    /** Для кадра: смотрим из дальней клетки квартиры на ЗАКРЫТУЮ дверь. */
    private static boolean forceDoor(Game g) throws Exception {
        Level lv = (Level) field(Game.class, "level").get(g);
        int[] dxs = {1, -1, 0, 0}, dys = {0, 0, 1, -1};
        for (int y = 1; y < lv.h - 1; y++) {
            for (int x = 1; x < lv.w - 1; x++) {
                if (lv.tile[lv.idx(x, y)] != Level.DOOR) continue;
                for (int k = 0; k < 4; k++) {
                    int ax = x + dxs[k], ay = y + dys[k];   // коридор
                    int bx = x - dxs[k], by = y - dys[k];   // первая клетка комнаты
                    if (lv.tile[lv.idx(ax, ay)] != Level.FLOOR) continue;
                    if (lv.tile[lv.idx(bx, by)] != Level.FLOOR) continue;
                    int dxc = bx - x, dyc = by - y;         // вглубь квартиры
                    int rx = bx + dxc, ry = by + dyc;       // дальняя клетка
                    if (lv.tile[lv.idx(rx, ry)] != Level.FLOOR) continue;
                    demoDoorX = x; demoDoorY = y;
                    placeForShot(g, lv, rx, ry, degOf(-dxc, -dyc));
                    return true;
                }
            }
        }
        return false;
    }

    /** Для кадра: распахиваем дверь — в проёме становится виден коридор. */
    private static void enterApartment(Game g) throws Exception {
        Level lv = (Level) field(Game.class, "level").get(g);
        lv.openDoor(demoDoorX, demoDoorY);
    }

    private static void placeForShot(Game g, Level lv, int px, int py, int deg)
            throws Exception {
        field(Game.class, "playerTX").setInt(g, px);
        field(Game.class, "playerTY").setInt(g, py);
        field(Game.class, "posX").setInt(g, px * Fx.FP + Fx.FP / 2);
        field(Game.class, "posY").setInt(g, py * Fx.FP + Fx.FP / 2);
        field(Game.class, "dirDeg").setInt(g, deg);
        java.lang.reflect.Method sv = Game.class.getDeclaredMethod("setVectors", new Class[0]);
        sv.setAccessible(true);
        sv.invoke(g, new Object[0]);
        for (int dy = -5; dy <= 5; dy++)
            for (int dx = -5; dx <= 5; dx++) lv.markSeen(px + dx, py + dy);
        lv.computeDist(px, py);
        for (int i = 0; i < lv.monsterCount; i++) lv.monsters[i].alive = false;
        field(Game.class, "message").set(g, "");
        field(Game.class, "messageUntil").setLong(g, 0L);
        field(Game.class, "hudDirty").setBoolean(g, true);
    }

    /** Для кадра/превью: показать дерево этажей на середине забега. */
    private static void forceProgress(Game g) throws Exception {
        Field fState = field(Game.class, "state");
        field(Game.class, "stage").setInt(g, 5);
        field(Game.class, "floorNumber").setInt(g, 560);
        field(Game.class, "fastFloors").setInt(g, 3);
        int[] row = (int[]) field(Game.class, "routeRow").get(g);
        int[] demo = {0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
        for (int i = 0; i < row.length; i++) row[i] = demo[i];
        field(Game.class, "routeFast").setBoolean(g, true);
        fState.setInt(g, 7);
    }

    /** Для кадра: ставим тварь за CRACKED-стеной и заставляем её пробить её. */
    private static void forceBreach(Game g) throws Exception {
        Field fl = field(Game.class, "level");
        Field fpx = field(Game.class, "playerTX");
        Field fpy = field(Game.class, "playerTY");
        Field fposx = field(Game.class, "posX");
        Field fposy = field(Game.class, "posY");
        Field fdeg = field(Game.class, "dirDeg");
        Level lv = (Level) fl.get(g);
        int[] dxs = {1, -1, 0, 0};
        int[] dys = {0, 0, 1, -1};
        for (int y = 1; y < lv.h - 1; y++) {
            for (int x = 1; x < lv.w - 1; x++) {
                if (!lv.isBreakable(x, y)) continue;
                for (int k = 0; k < 4; k++) {
                    int mx = x - dxs[k], my = y - dys[k];
                    int px = x + dxs[k], py = y + dys[k];
                    if (lv.isBlocking(mx, my) || lv.isBlocking(px, py)) continue;
                    Monster m = lv.monsters[0];
                    m.place(mx, my);
                    for (int i = 1; i < lv.monsterCount; i++) lv.monsters[i].alive = false;
                    fpx.setInt(g, px);
                    fpy.setInt(g, py);
                    fposx.setInt(g, px * 4096 + 2048);
                    fposy.setInt(g, py * 4096 + 2048);
                    int vx = x - px, vy = y - py;
                    fdeg.setInt(g, vx > 0 ? 180 : (vx < 0 ? 0 : (vy > 0 ? 270 : 90)));
                    method(Game.class, "setVectors", new Class[0]).invoke(g, new Object[0]);
                    for (int yy = py - 2; yy <= py + 2; yy++) {
                        for (int xx = px - 2; xx <= px + 2; xx++) lv.markSeen(xx, yy);
                    }
                    Method tb = method(Game.class, "tryBreachWall",
                            new Class[] {Monster.class, long.class});
                    boolean ok = ((Boolean) tb.invoke(g, new Object[] {m,
                            new Long(System.currentTimeMillis() + 10000L)})).booleanValue();
                    if (ok) return;
                }
            }
        }
    }

    private static Field field(Class c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Method method(Class c, String name, Class[] sig) throws Exception {
        Method m = c.getDeclaredMethod(name, sig);
        m.setAccessible(true);
        return m;
    }

    /** Ставит тварь в паре клеток перед игроком — только для скриншота. */
    private static int placeMonsterInFront(Game g) throws Exception {
        Field fl = Game.class.getDeclaredField("level"); fl.setAccessible(true);
        Field ftx = Game.class.getDeclaredField("playerTX"); ftx.setAccessible(true);
        Field fty = Game.class.getDeclaredField("playerTY"); fty.setAccessible(true);
        Field fdx = Game.class.getDeclaredField("dirX"); fdx.setAccessible(true);
        Field fdy = Game.class.getDeclaredField("dirY"); fdy.setAccessible(true);
        Level lv = (Level) fl.get(g);
        int px = ftx.getInt(g), py = fty.getInt(g);
        int dx = fdx.getInt(g) >> 12, dy = fdy.getInt(g) >> 12;
        int mx = px, my = py;
        for (int step = 1; step <= 2; step++) {
            int nx = px + dx * step, ny = py + dy * step;
            if (lv.isBlocking(nx, ny)) break;   // не ставить за стеной/дверью
            mx = nx; my = ny;
        }
        // Самая крупная/яркая тварь уровня — в центр коридора
        int best = 0;
        for (int i = 1; i < lv.monsterCount; i++) {
            if (lv.monsters[i].head == 2 || lv.monsters[i].arms == 1) { best = i; break; }
        }
        lv.monsters[best].place(mx, my);
        lv.monsters[best].hp = 1;      // скриншот выстрела показывает разлёт
        for (int i = 0; i < lv.monsterCount; i++) {
            if (i != best) lv.monsters[i].alive = false;
        }
        lv.markSeen(mx, my);
        for (int step = 1; step <= 4; step++) {
            lv.markSeen(px + dx * step, py + dy * step);
        }
        return best;
    }

    private static void tap(Game g, int code) {
        g.injectKey(code, true);
        g.injectKey(code, false);
    }

    private static void save(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
        System.out.println("  " + f);
    }
}
