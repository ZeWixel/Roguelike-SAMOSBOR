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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * ПРЕВЬЮ: запускает тот же самый J2ME-код на JDK 8 через заглушки MIDP.
 * 1) игровой цикл рисует в BufferedImage;
 * 2) HttpServer отдаёт кадры как PNG и принимает нажатия клавиш;
 * 3) браузер показывает живой экран 240x320 с управлением с клавиатуры.
 * Это не порт на JS — в браузере играет скомпилированный samosbor.Game.
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
        synchronized (g) { g.injectKey(code, down); }
        send(ex, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------- режим скриншотов

    private static void capture(String dir) throws Exception {
        File d = new File(dir);
        d.mkdirs();
        Game g = new Game(null);
        BufferedImage img = frame;
        Graphics g2 = gfx;
        g.paint(g2);
        save(img, new File(d, "01-title.png"));
        tap(g, 53); g.paint(g2);                       // НОВЫЙ ЗАБЕГ
        save(img, new File(d, "02-brief.png"));
        tap(g, 53); g.paint(g2);                       // НАЧАТЬ
        save(img, new File(d, "03-play.png"));          // вид из коридора

        placeMonsterInFront(g);
        g.paint(g2);
        save(img, new File(d, "04-monster.png"));       // процедурная тварь

        tap(g, 53); g.paint(g2);                       // выстрел
        save(img, new File(d, "05-shot.png"));

        tap(g, 42); g.paint(g2);                       // * = карта
        save(img, new File(d, "06-map.png"));
        tap(g, 42); g.paint(g2);                       // закрыть карту

        tap(g, 35); g.paint(g2);                       // # = очки Virtual Boy
        save(img, new File(d, "07-virtualboy.png"));
        System.out.println("capture done -> " + dir);
    }

    /** Ставит тварь в паре клеток перед игроком — только для скриншота. */
    private static void placeMonsterInFront(Game g) throws Exception {
        Field fl = Game.class.getDeclaredField("level"); fl.setAccessible(true);
        Field ftx = Game.class.getDeclaredField("playerTX"); ftx.setAccessible(true);
        Field fty = Game.class.getDeclaredField("playerTY"); fty.setAccessible(true);
        Field fdx = Game.class.getDeclaredField("dirX"); fdx.setAccessible(true);
        Field fdy = Game.class.getDeclaredField("dirY"); fdy.setAccessible(true);
        Level lv = (Level) fl.get(g);
        int px = ftx.getInt(g), py = fty.getInt(g);
        int dx = fdx.getInt(g) >> 12, dy = fdy.getInt(g) >> 12;
        int mx = px, my = py;
        for (int step = 1; step <= 4; step++) {
            int nx = px + dx * step, ny = py + dy * step;
            if (lv.isWall(nx, ny)) break;   // не ставить за стеной
            mx = nx; my = ny;
        }
        // Самая крупная/яркая тварь уровня — в центр коридора
        int best = 0;
        for (int i = 1; i < lv.monsterCount; i++) {
            if (lv.monsters[i].head == 2 || lv.monsters[i].arms == 1) { best = i; break; }
        }
        lv.monsters[best].place(mx, my);
        for (int i = 0; i < lv.monsterCount; i++) {
            if (i != best) lv.monsters[i].alive = false;
        }
        lv.markSeen(mx, my);
        for (int step = 1; step <= 4; step++) {
            lv.markSeen(px + dx * step, py + dy * step);
        }
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
