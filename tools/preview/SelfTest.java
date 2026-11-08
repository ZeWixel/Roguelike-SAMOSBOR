package samosbor;

import javax.microedition.lcdui.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

/**
 * Автоматическая проверка игры без эмулятора (на MIDP-заглушках):
 *  - 60 процедурных этажей: связность, доступность ключа/гермодвери, расстановка;
 *  - пароли: encode/decode для всех комбинаций;
 *  - спрайты монстров из всех видов частей тела;
 *  - полный пробег: 13 этажей от старта до концовки A.
 */
public final class SelfTest {

    private static int checks = 0, failures = 0;

    public static void main(String[] args) throws Exception {
        testLevels();
        testPasswords();
        testMonsters();
        testFullRun();
        System.out.println("SelfTest: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static void testLevels() {
        for (int n = 0; n < 60; n++) {
            int seed = 0x1a2b000 | (n * 37 + 11);
            int stage = n % 13;
            int floor = 540 + (n * 7) % 46;
            Level lv = new Level(seed, floor, stage);
            lv.computeDist(lv.startX, lv.startY);
            int unreachable = 0, floors = 0;
            for (int i = 0; i < lv.tile.length; i++) {
                if (lv.tile[i] != Level.FLOOR) continue;
                floors++;
                if (lv.dist[i] < 0) unreachable++;
            }
            check(unreachable == 0, "level " + n + ": " + unreachable + " unreachable floor cells");
            check(floors > 40, "level " + n + ": too few floor cells (" + floors + ")");
            check(lv.tile[lv.idx(lv.exitX, lv.exitY)] == Level.EXIT,
                    "level " + n + ": exit tile missing");
            check(lv.tile[lv.idx(lv.approachX, lv.approachY)] == Level.FLOOR,
                    "level " + n + ": exit approach blocked");
            int d = Math.abs(lv.exitX - lv.approachX) + Math.abs(lv.exitY - lv.approachY);
            check(d == 1, "level " + n + ": exit not adjacent to approach");
            check(lv.tile[lv.idx(lv.keyX, lv.keyY)] == Level.FLOOR,
                    "level " + n + ": key not on floor");
            boolean keyFound = false;
            for (int i = 0; i < lv.itemCount; i++) {
                if (lv.itemType[i] == Level.ITEM_KEY) keyFound = true;
                check(lv.tile[lv.idx(lv.itemX[i], lv.itemY[i])] == Level.FLOOR,
                        "level " + n + ": item in a wall");
                for (int j = i + 1; j < lv.itemCount; j++) {
                    if (lv.itemX[i] == lv.itemX[j] && lv.itemY[i] == lv.itemY[j]) {
                        check(false, "level " + n + ": duplicate item tile");
                    }
                }
            }
            check(keyFound, "level " + n + ": no key item");
            for (int i = 0; i < lv.monsterCount; i++) {
                check(lv.tile[lv.idx(lv.monsters[i].tileX, lv.monsters[i].tileY)] == Level.FLOOR,
                        "level " + n + ": monster spawned in wall");
            }
            check(lv.timeLimitSec >= 70 && lv.timeLimitSec <= 210,
                    "level " + n + ": bad time limit " + lv.timeLimitSec);
        }
        System.out.println("  levels: ok");
    }

    private static void testPasswords() {
        for (int seed = 0; seed < 1024; seed += 7) {
            for (int stage = 0; stage <= 12; stage++) {
                int floor = 540 + ((seed * 3 + stage * 5) % 32);
                String p = Passwords.encode(0x1a2b000 | seed, stage, floor);
                int[] out = new int[3];
                boolean ok = Passwords.decode(p, out);
                check(ok, "password decode " + p);
                if (ok) {
                    check(out[0] == (0x1a2b000 | seed) && out[1] == stage && out[2] == floor,
                            "password roundtrip " + p + " -> " + out[0] + "/" + out[1] + "/" + out[2]);
                }
                check(p.length() == 4, "password length " + p);
            }
        }
        int[] bad = new int[3];
        check(!Passwords.decode("ZZZ", bad), "short password rejected");
        check(!Passwords.decode("!!!!", bad), "bad symbols rejected");
        System.out.println("  passwords: ok");
    }

    private static void testMonsters() {
        Rng r = new Rng(0x51a7);
        int nonEmpty = 0;
        for (int i = 0; i < 240; i++) {
            Monster m = Monster.create(r, i % 13);
            boolean hasPixels = false;
            for (int k = 0; k < m.frames[0].p.length; k++) {
                if ((m.frames[0].p[k] >>> 24) != 0) { hasPixels = true; break; }
            }
            if (hasPixels) nonEmpty++;
            check(m.frames[0].vb != null && m.frames[1].vb != null,
                    "monster " + i + ": VB variant missing");
            check(m.name.length() > 2 && m.hp > 0 && m.speedMs > 0,
                    "monster " + i + ": bad stats");
            check(m.description().indexOf("/") > 0, "monster " + i + ": no parts description");
        }
        check(nonEmpty == 240, "all generated monsters have visible pixels");
        System.out.println("  monsters: ok (" + nonEmpty + " sprites)");
    }

    // ----------------------------------------------------------- полный пробег

    private static Field field(Class c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void testFullRun() throws Exception {
        BufferedImage img = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics gfx = new Graphics(img);
        Game g = new Game(null);
        Field fState = field(Game.class, "state");
        Field fLevel = field(Game.class, "level");
        Field fPX = field(Game.class, "playerTX");
        Field fPY = field(Game.class, "playerTY");
        Field fDeg = field(Game.class, "dirDeg");
        Field fHealth = field(Game.class, "health");
        Field fEnding = field(Game.class, "ending");
        Field fStage = field(Game.class, "stage");
        Field fKey = field(Game.class, "hasKey");

        tap(g, 53); // TITLE -> BRIEF
        check(fState.getInt(g) == 2, "state BRIEF after new game");
        tap(g, 53); // BRIEF -> PLAY
        check(fState.getInt(g) == 3, "state PLAY after start");

        for (int stage = 0; stage < 13; stage++) {
            Level lv = (Level) fLevel.get(g);
            fHealth.setInt(g, 5);
            // убираем тварей, чтобы они не перекрывали тестовый маршрут
            for (int i = 0; i < lv.monsterCount; i++) lv.monsters[i].alive = false;
            walk(g, lv, fPX.getInt(g), fPY.getInt(g), lv.keyX, lv.keyY, fDeg, fPX, fPY);
            check(fKey.getBoolean(g), "stage " + stage + ": key picked up");
            walk(g, lv, fPX.getInt(g), fPY.getInt(g), lv.approachX, lv.approachY, fDeg, fPX, fPY);
            // встать лицом к гермодвери и шагнуть в неё
            int ex = lv.exitX - lv.approachX, ey = lv.exitY - lv.approachY;
            int want = ex > 0 ? 180 : (ex < 0 ? 0 : (ey > 0 ? 270 : 90));
            int guard = 0;
            while (fDeg.getInt(g) != want && guard++ < 5) {
                int diff = (want - fDeg.getInt(g) + 360) % 360;
                tap(g, diff == 90 ? 54 : 52);
            }
            tap(g, 50);
            g.paint(gfx);
            g.paint(gfx);
            int st = fState.getInt(g);
            check(st == 2 || st == 5, "stage " + stage + ": reached BRIEF or ENDING, got " + st);
            if (st == 5) break;
            tap(g, 53); // BRIEF -> PLAY
        }
        check(fState.getInt(g) == 5, "full run reaches an ending");
        check(fStage.getInt(g) == 13, "full run clears 13 floors");
        check(fEnding.getInt(g) == Endings.A, "fast flawless run gives ending A");
        System.out.println("  full run: ok, ending " + fEnding.getInt(g));
    }

    /** Обход по BFS: повороты 4/6 и шаги 2. */
    private static void walk(Game g, Level lv, int sx, int sy, int gx, int gy,
                             Field fDeg, Field fPX, Field fPY) throws Exception {
        int[] path = path(lv, sx, sy, gx, gy);
        for (int n = 1; n < path.length; n++) {
            int cur = path[n - 1], next = path[n];
            int cx = cur % lv.w, cy = cur / lv.w;
            int nx = next % lv.w, ny = next / lv.w;
            int want = (nx > cx) ? 180 : (nx < cx ? 0 : (ny > cy ? 270 : 90));
            int guard = 0;
            while (fDeg.getInt(g) != want && guard++ < 5) {
                int deg = fDeg.getInt(g);
                int diff = (want - deg + 360) % 360;
                tap(g, diff == 90 ? 54 : 52); // 6 вправо / 4 влево
            }
            tap(g, 50);
        }
    }

    private static int[] path(Level lv, int sx, int sy, int gx, int gy) {
        int n = lv.w * lv.h;
        int[] prev = new int[n];
        for (int i = 0; i < n; i++) prev[i] = -2;
        int[] q = new int[n];
        int head = 0, tail = 0;
        int s = lv.idx(sx, sy), goal = lv.idx(gx, gy);
        prev[s] = -1;
        q[tail++] = s;
        while (head < tail) {
            int cur = q[head++];
            if (cur == goal) break;
            int x = cur % lv.w, y = cur / lv.w;
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                int ny = y + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                if (lv.isWall(nx, ny)) continue;
                int ni = lv.idx(nx, ny);
                if (prev[ni] != -2) continue;
                prev[ni] = cur;
                q[tail++] = ni;
            }
        }
        if (prev[goal] == -2) return new int[]{s}; // недостижимо (не должно случиться)
        int[] rev = new int[n];
        int cnt = 0;
        int p = goal;
        while (p != -1 && cnt < n) {
            rev[cnt++] = p;
            if (p == s) break;
            p = prev[p];
        }
        int[] out = new int[cnt];
        for (int i = 0; i < cnt; i++) out[i] = rev[cnt - 1 - i];
        return out;
    }

    private static void tap(Game g, int code) {
        g.injectKey(code, true);
        g.injectKey(code, false);
    }
}
