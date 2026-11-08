package samosbor;

import javax.microedition.lcdui.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Автоматическая проверка игры без эмулятора (на MIDP-заглушках):
 *  - 60 процедурных этажей: связность, доступность ключа/гермодвери, расстановка;
 *  - автосохранение RMS: снимок, восстановление уровня/тварей, «продолжить»;
 *  - спрайты монстров из всех видов частей тела;
 *  - новый ИИ: телеграфированный укус, 1 сердце, толчок не ранит;
 *  - старт на J2ME Loader: getKeyName() бросает «unknown keycode -9»;
 *  - широкие коридоры Insmouse no Yakata: двуклеточные проходы, залы,
 *    связность и место для бокового манёвра тварей;
 *  - карта продвижения: 45 узлов ROM, 13 колонок, 4 концовки, переходы;
 *  - плавный доворот камеры: дробные sin/cos, ease-in-out, логика ровно 90°;
 *  - полный пробег: 13 этажей от старта до концовки A.
 */
public final class SelfTest {

    private static int checks = 0, failures = 0;

    public static void main(String[] args) throws Exception {
        testJ2MELoaderStartup();
        testLevels();
        testSave();
        testMonsters();
        testEnemyAI();
        testPauseAndCrosshair();
        testRomMechanics();
        testCorridors();
        testProgressChart();
        testSmoothTurn();
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

    /** Пароли удалены: вместо них RMS-автосейв и «Продолжить». */
    private static void testSave() throws Exception {
        Save.clear();
        check(!Save.hasAuto(), "clean start has no autosave");

        Game g = new Game(null);
        tap(g, 53);  // меню: НОВЫЙ ЗАБЕГ
        tap(g, 53);  // BRIEF -> PLAY
        Field fHealth = field(Game.class, "health");
        Field fMag = field(Game.class, "magazine");
        Field fKey = field(Game.class, "hasKey");
        Field fFloor = field(Game.class, "floorNumber");
        Field fPX = field(Game.class, "playerTX");
        Field fPY = field(Game.class, "playerTY");
        Field fDeg = field(Game.class, "dirDeg");
        Field fLevel = field(Game.class, "level");

        fHealth.setInt(g, 2);
        fMag.setInt(g, 3);
        fKey.setBoolean(g, true);
        fFloor.setInt(g, 561);
        Level before = (Level) fLevel.get(g);
        before.seen[before.idx(before.keyX, before.keyY)] = true;

        Method autosave = method(Game.class, "autosave", new Class[0]);
        autosave.invoke(g, new Object[0]);
        byte[] snap = Save.readAuto();
        check(snap != null && snap.length > 512, "autosave snapshot written (" +
                (snap == null ? 0 : snap.length) + " bytes)");
        check(Save.readFloor() != null, "floor checkpoint written");
        check(Save.hasAuto(), "hasAuto() after autosave");

        Game g2 = new Game(null);
        Method restore = method(Game.class, "restore", new Class[] {byte[].class});
        boolean ok = ((Boolean) restore.invoke(g2, new Object[] {snap})).booleanValue();
        check(ok, "restore() accepts valid snapshot");
        check(fHealth.getInt(g2) == 2, "restore: health");
        check(fMag.getInt(g2) == 3, "restore: magazine");
        check(fKey.getBoolean(g2), "restore: key flag");
        check(fFloor.getInt(g2) == 561, "restore: floor number");
        check(fPX.getInt(g2) == before.startX && fPY.getInt(g2) == before.startY,
                "restore: player tile");
        Level after = (Level) fLevel.get(g2);
        check(after.w == before.w && after.h == before.h, "restore: level size");
        boolean tilesSame = true, seenSame = true;
        for (int i = 0; i < before.tile.length; i++) {
            if (before.tile[i] != after.tile[i]) tilesSame = false;
            if (before.seen[i] != after.seen[i]) seenSame = false;
        }
        check(tilesSame, "restore: tiles identical");
        check(seenSame, "restore: explored map identical");
        check(after.itemCount == before.itemCount, "restore: item count");
        check(after.monsterCount == before.monsterCount, "restore: monster count");
        boolean monstersSame = true;
        for (int i = 0; i < after.monsterCount; i++) {
            Monster a = before.monsters[i], b = after.monsters[i];
            if (a.head != b.head || a.torso != b.torso || a.arms != b.arms ||
                    a.legs != b.legs || a.extra != b.extra || a.hp != b.hp ||
                    a.tileX != b.tileX || a.tileY != b.tileY || a.alive != b.alive) {
                monstersSame = false;
            }
        }
        check(monstersSame, "restore: procedural monsters rebuilt identically");

        Method cont = method(Game.class, "continueGame", new Class[0]);
        Game g3 = new Game(null);
        check(((Boolean) cont.invoke(g3, new Object[0])).booleanValue(),
                "menu 'Continue' resumes from autosave");
        check(field(Game.class, "state").getInt(g3) == 3, "continue enters PLAY");

        check(!((Boolean) restore.invoke(new Game(null),
                new Object[] {new byte[] {1, 2, 3}})).booleanValue(),
                "restore() rejects garbage");
        Save.clear();
        check(Save.readAuto() == null && Save.readFloor() == null, "clear() removes saves");
        System.out.println("  RMS autosave: ok");
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
            check(m.damage == 1, "monster " + i + ": bite must cost 1 heart");
            check(m.maxHp >= 2 && m.maxHp <= 8,
                    "monster " + i + ": ROM needs 2-8 bullets per monster");
            check(m.speedMs >= 1150 && m.sight <= 5,
                    "monster " + i + ": slow, short-sighted AI stats");
        }
        check(nonEmpty == 240, "all generated monsters have visible pixels");
        System.out.println("  monsters: ok (" + nonEmpty + " sprites)");
    }

    /** Тварь не должна «выкашивать» игрока за секунду. */
    private static void testEnemyAI() throws Exception {
        Game g = new Game(null);
        tap(g, 53);
        tap(g, 53);
        Field fLevel = field(Game.class, "level");
        Field fPX = field(Game.class, "playerTX");
        Field fPY = field(Game.class, "playerTY");
        Field fHealth = field(Game.class, "health");
        Field fDeg = field(Game.class, "dirDeg");
        Level lv = (Level) fLevel.get(g);
        int px = fPX.getInt(g), py = fPY.getInt(g);

        int ax = -1, ay = -1;
        int[] dxs = {1, -1, 0, 0};
        int[] dys = {0, 0, 1, -1};
        for (int k = 0; k < 4; k++) {
            if (!lv.isWall(px + dxs[k], py + dys[k])) {
                ax = px + dxs[k]; ay = py + dys[k];
                break;
            }
        }
        check(ax >= 0, "AI test: player has a free neighbour");
        for (int i = 1; i < lv.monsterCount; i++) {
            lv.monsters[i].alive = false;
            lv.monsters[i].respawnAt = 0;
        }
        Monster m = lv.monsters[0];
        m.place(ax, ay);
        m.hp = m.maxHp = 99;
        m.sight = 5;
        m.nextThink = Long.MAX_VALUE;
        m.nextAttack = 0;
        m.windupUntil = 0;
        m.fleeUntil = 0;
        check(lv.lineOfSight(ax, ay, px, py), "AI test: direct line of sight");

        Method update = method(Game.class, "update", new Class[] {long.class, long.class});
        long fakeNow = 100000L;
        fHealth.setInt(g, 3);
        for (int i = 0; i < 60; i++) {
            fakeNow += 100;
            update.invoke(g, new Object[] {new Long(100), new Long(fakeNow)});
        }
        int hp = fHealth.getInt(g);
        check(hp >= 1, "slow AI does not kill in 6 seconds (health=" + hp + ")");
        check(hp <= 2, "telegraphed bite still lands one hit (health=" + hp + ")");
        check(m.attackCooldown >= 5600, "long cooldown between bites");

        // Толчок в тварь закрывает коридор, но НЕ снимает сердце.
        int want = ax > px ? 180 : (ax < px ? 0 : (ay > py ? 270 : 90));
        fDeg.setInt(g, want);
        method(Game.class, "setVectors", new Class[0]).invoke(g, new Object[0]);
        int before = fHealth.getInt(g);
        tap(g, 50);
        check(fHealth.getInt(g) == before, "bumping a monster deals no damage");
        check(fPX.getInt(g) == px && fPY.getInt(g) == py, "bump does not move player");
        Save.clear();
        System.out.println("  enemy AI: ok (health " + hp + " after 6 s)");
    }

    // ------------------------------------------- пауза и возврат прицела в центр

    private static void testPauseAndCrosshair() throws Exception {
        Save.clear();
        BufferedImage img = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics gfx = new Graphics(img);
        Game g = new Game(null);
        tap(g, 53);
        tap(g, 53);
        Field fState = field(Game.class, "state");
        Field fAiming = field(Game.class, "aiming");
        Field fAimX = field(Game.class, "aimX");
        Field fAimY = field(Game.class, "aimY");
        Field fW = field(Game.class, "w");
        Field fViewH = field(Game.class, "viewH");
        Field fTime = field(Game.class, "timeLeftMs");

        tap(g, 55);                       // достать оружие
        check(fAiming.getBoolean(g), "7 draws weapon");
        fAimX.setInt(g, 17);
        fAimY.setInt(g, 33);
        tap(g, 55);                       // убрать оружие
        check(!fAiming.getBoolean(g), "7 holsters weapon");
        check(fAimX.getInt(g) == fW.getInt(g) >> 1 &&
                fAimY.getInt(g) == fViewH.getInt(g) >> 1,
                "crosshair returns to screen centre after holstering");

        tap(g, -7);                       // правая софт-клавиша
        check(fState.getInt(g) == 6, "right soft key opens PAUSE menu");
        long before = fTime.getLong(g);
        g.paint(gfx);
        g.paint(gfx);
        g.paint(gfx);
        check(fTime.getLong(g) == before, "timer frozen while paused");
        tap(g, -7);
        check(fState.getInt(g) == 3, "right soft key resumes PLAY");
        Save.clear();
        System.out.println("  pause + crosshair: ok");
    }

    // ---------------------------------------------- сверка с ROM Insmouse no Yakata

    private static void testRomMechanics() throws Exception {
        Save.clear();
        Game g = new Game(null);
        tap(g, 53);
        tap(g, 53);
        Field fLevel = field(Game.class, "level");
        Field fRenderer = field(Game.class, "renderer");
        Field fAiming = field(Game.class, "aiming");
        Field fAimX = field(Game.class, "aimX");
        Field fAimY = field(Game.class, "aimY");
        Field fMag = field(Game.class, "magazine");
        Field fShowMap = field(Game.class, "showMap");
        Field fTime = field(Game.class, "timeLeftMs");
        Level lv = (Level) fLevel.get(g);
        Renderer r = (Renderer) fRenderer.get(g);

        // ROM/FAQ: «Monsters need to be shot with 2-8 bullets», выстрел = 1 HP.
        r.sprCount = 1;
        r.sprIndex[0] = 0;
        r.sprLeft[0] = 0;   r.sprRight[0] = 240;
        r.sprTop[0] = 0;    r.sprBottom[0] = 264;
        r.sprDepth[0] = 100;
        fAiming.setBoolean(g, true);
        fAimX.setInt(g, 120);
        fAimY.setInt(g, 132);
        fMag.setInt(g, 20);
        Monster target = lv.monsters[0];
        target.hp = target.maxHp;
        target.alive = true;
        Method fire = method(Game.class, "fire", new Class[] {long.class});
        int shots = 0;
        while (target.alive && shots < 12) {
            fire.invoke(g, new Object[] {new Long(System.currentTimeMillis())});
            shots++;
        }
        check(!target.alive, "monster can be killed");
        check(shots == target.maxHp, "exactly maxHp bullets kill it (" + shots + "/" + target.maxHp + ")");
        check(shots >= 2 && shots <= 8, "bullet count within ROM's 2-8 range");
        r.sprCount = 0;

        // ROM/FAQ: «The timer stops while you are on the map screen».
        fShowMap.setBoolean(g, true);
        long t0 = fTime.getLong(g);
        Method update = method(Game.class, "update", new Class[] {long.class, long.class});
        update.invoke(g, new Object[] {new Long(5000), new Long(300000L)});
        check(fTime.getLong(g) == t0, "timer stops on map screen");
        fShowMap.setBoolean(g, false);

        // ROM/FAQ: «sometimes a monster will break through a wall».
        Game g2 = new Game(null);
        tap(g2, 53);
        tap(g2, 53);
        Level lv2 = (Level) fLevel.get(g2);
        Field fPX = field(Game.class, "playerTX");
        Field fPY = field(Game.class, "playerTY");
        Field fPosX = field(Game.class, "posX");
        Field fPosY = field(Game.class, "posY");
        Field fHealth = field(Game.class, "health");
        int[] dxs = {1, -1, 0, 0};
        int[] dys = {0, 0, 1, -1};
        int wx = -1, wy = -1, px = -1, py = -1, mx = -1, my = -1;
        for (int y = 1; y < lv2.h - 1 && wx < 0; y++) {
            for (int x = 1; x < lv2.w - 1 && wx < 0; x++) {
                if (!lv2.isBreakable(x, y)) continue;
                for (int k = 0; k < 4; k++) {
                    int ax = x - dxs[k], ay = y - dys[k];
                    int bx = x + dxs[k], by = y + dys[k];
                    if (lv2.isWall(ax, ay) || lv2.isWall(bx, by)) continue;
                    wx = x; wy = y; mx = ax; my = ay; px = bx; py = by;
                    break;
                }
            }
        }
        if (wx < 0) {
            // На редком сиде трещин может не оказаться рядом с коридором — создаём «B».
            for (int y = 1; y < lv2.h - 1 && wx < 0; y++) {
                for (int x = 1; x < lv2.w - 1 && wx < 0; x++) {
                    if (!lv2.isWall(x, y)) continue;
                    if (!lv2.isWall(x - 1, y) && !lv2.isWall(x + 1, y)) {
                        lv2.tile[lv2.idx(x, y)] = Level.CRACKED;
                        wx = x; wy = y; mx = x - 1; my = y; px = x + 1; py = y;
                    }
                }
            }
        }
        check(wx >= 0, "ROM test: found a breakable ('B') wall between corridors");
        if (wx < 0) { Save.clear(); return; }

        for (int i = 1; i < lv2.monsterCount; i++) {
            lv2.monsters[i].alive = false;
            lv2.monsters[i].respawnAt = 0;
        }
        Monster ambusher = lv2.monsters[0];
        ambusher.place(mx, my);
        ambusher.nextBreach = 1;                 // кулдаун уже истёк
        ambusher.nextThink = Long.MAX_VALUE;     // только пробой, без блуждания
        ambusher.nextAttack = Long.MAX_VALUE;
        ambusher.windupUntil = 0;
        ambusher.fleeUntil = 0;
        ambusher.noticedUntil = 0;
        fPX.setInt(g2, px);
        fPY.setInt(g2, py);
        fPosX.setInt(g2, px * 4096 + 2048);
        fPosY.setInt(g2, py * 4096 + 2048);
        fHealth.setInt(g2, 3);
        lv2.computeDist(px, py);
        check(!lv2.lineOfSight(mx, my, px, py), "ROM test: wall blocks line of sight");

        long fakeNow = 400000L;
        boolean breached = false;
        for (int i = 0; i < 40 && !breached; i++) {
            fakeNow += 100;
            update.invoke(g2, new Object[] {new Long(100), new Long(fakeNow)});
            breached = lv2.tile[lv2.idx(wx, wy)] == Level.FLOOR;
        }
        check(breached, "monster breaks through the CRACKED wall");
        for (int i = 0; i < 25; i++) {
            fakeNow += 100;
            update.invoke(g2, new Object[] {new Long(100), new Long(fakeNow)});
        }
        check(fHealth.getInt(g2) == 2, "wall breach ends in exactly one heart lost");
        check(ambusher.tileX == wx && ambusher.tileY == wy,
                "monster steps into the hole it made");
        Save.clear();
        System.out.println("  ROM mechanics: ok");
    }

    // ----------------------------------------------------------- полный пробег

    /**
     * Регрессия из лога J2ME Loader: RuntimeException: Failed startApp вызвана
     * IllegalArgumentException: unknown keycode -9 в Canvas.getKeyName() ->
     * Game.detectSoftKeys(). Строгая заглушка в tools/preview/stub теперь
     * бросает то же исключение на неизвестных кодах, поэтому каждый new Game()
     * в этом файле проверяет и исправление тоже.
     */
    private static void testJ2MELoaderStartup() throws Exception {
        Game g = new Game(null);
        boolean strict = false;
        try {
            g.getKeyName(-9);
        } catch (IllegalArgumentException e) {
            strict = e.getMessage() != null &&
                    e.getMessage().indexOf("unknown keycode") >= 0;
        }
        check(strict, "stub reproduces J2ME Loader unknown keycode -9");
        check(true, "Game() survives strict getKeyName() in startApp");
        Field fSoft = field(Game.class, "K_SOFT_RIGHT");
        check(fSoft.getInt(g) == -7, "right soft key still found by name");
        Method norm = method(Game.class, "norm", new Class[] {int.class});
        check(((Integer) norm.invoke(g, new Object[] {new Integer(-7)})).intValue() == 12,
                "right soft key still maps to pause");
        check(((Integer) norm.invoke(g, new Object[] {new Integer(-9)})).intValue() == -1,
                "unknown keycode -9 is ignored, not fatal");
        check(((Integer) norm.invoke(g, new Object[] {new Integer(53)})).intValue() == 4,
                "keypad still works after safe key-name lookup");
        for (int k = -1; k >= -32; k--) {
            norm.invoke(g, new Object[] {new Integer(k)});
        }
        check(true, "norm() survives every -1..-32 vendor keycode");

        // Худший случай: getKeyName() не знает вообще ни одного вендорского
        // кода. Раскладка по умолчанию и явный fallback -7 должны выжить.
        javax.microedition.lcdui.Canvas.rejectAllVendorNames = true;
        try {
            Game g2 = new Game(null);
            check(fSoft.getInt(g2) == -7,
                    "right soft key falls back to -7 when all names are rejected");
            check(((Integer) norm.invoke(g2, new Object[] {new Integer(-7)})).intValue() == 12,
                    "pause still works with no key names at all");
            check(((Integer) norm.invoke(g2, new Object[] {new Integer(50)})).intValue() == 0,
                    "numeric keypad works with no key names at all");
        } finally {
            javax.microedition.lcdui.Canvas.rejectAllVendorNames = false;
        }
        Save.clear();
        System.out.println("  J2ME Loader startup: ok (unknown keycode -9 tolerated)");
    }

    /** Ширина коридоров, залы, связность и место для манёвра тварей. */
    private static void testCorridors() {
        double worstWide = 1.0, worstSide = 1.0, fewestWalls = 1.0, worstMonN = 99.0;
        int fewestBlocks = 1 << 30, mostBlocks = 0;
        for (int n = 0; n < 60; n++) {
            Level lv = new Level(0x1a2b000 | (n * 37 + 11),
                    540 + (n * 7) % 46, n % 13);
            lv.computeDist(lv.startX, lv.startY);
            int floors = 0, walls = 0, wide = 0, side = 0, blocks3x3 = 0, unreachable = 0;
            for (int y = 1; y < lv.h - 1; y++) {
                for (int x = 1; x < lv.w - 1; x++) {
                    int i = lv.idx(x, y);
                    if (lv.tile[i] != Level.FLOOR) { walls++; continue; }
                    floors++;
                    if (lv.dist[i] < 0) unreachable++;
                    int open9 = 0, deg = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (lv.tile[lv.idx(x + dx, y + dy)] == Level.FLOOR) open9++;
                        }
                    }
                    for (int k = 0; k < 4; k++) {
                        int nx = x + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                        int ny = y + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                        if (lv.tile[lv.idx(nx, ny)] == Level.FLOOR) deg++;
                    }
                    if (open9 >= 5) wide++;   // двуклеточный проход, а не щель
                    if (deg >= 3) side++;     // есть клетка для шага вбок
                }
            }
            for (int y = 1; y < lv.h - 2; y++) {
                for (int x = 1; x < lv.w - 2; x++) {
                    boolean all = true;
                    for (int dy = 0; dy < 3 && all; dy++)
                        for (int dx = 0; dx < 3; dx++)
                            if (lv.tile[lv.idx(x + dx, y + dy)] != Level.FLOOR) {
                                all = false; break;
                            }
                    if (all) blocks3x3++;
                }
            }
            double monN = 0;
            for (int i = 0; i < lv.monsterCount; i++) {
                Monster m = lv.monsters[i];
                for (int k = 0; k < 4; k++) {
                    int nx = m.tileX + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                    int ny = m.tileY + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                    if (!lv.isWall(nx, ny)) monN++;
                }
            }
            monN /= lv.monsterCount;
            double wideR = floors == 0 ? 0 : (double) wide / floors;
            double sideR = floors == 0 ? 0 : (double) side / floors;
            double wallR = (double) walls / ((lv.w - 2) * (lv.h - 2));
            if (wideR < worstWide) worstWide = wideR;
            if (sideR < worstSide) worstSide = sideR;
            if (wallR < fewestWalls) fewestWalls = wallR;
            if (monN < worstMonN) worstMonN = monN;
            if (blocks3x3 < fewestBlocks) fewestBlocks = blocks3x3;
            if (blocks3x3 > mostBlocks) mostBlocks = blocks3x3;
            check(wideR >= 0.80, "level " + n + ": only " +
                    (int) (wideR * 100) + "% wide corridor cells");
            check(sideR >= 0.78, "level " + n + ": only " +
                    (int) (sideR * 100) + "% cells with a side step");
            check(blocks3x3 >= 6, "level " + n + ": no hall for flanking");
            check(blocks3x3 <= 80, "level " + n + ": " + blocks3x3 +
                    " 3x3 halls, level is an open field");
            check(wallR >= 0.18, "level " + n + ": only " +
                    (int) (wallR * 100) + "% walls");
            check(monN >= 2.0, "level " + n + ": monsters average " + monN +
                    " free neighbours, no room to maneuver");
            check(unreachable == 0, "level " + n + ": wide layout is disconnected");
        }
        System.out.println("  corridors: ok, min " + (int) (worstWide * 100) +
                "% wide cells, " + (int) (worstSide * 100) +
                "% side-step cells, " + fewestBlocks + ".." + mostBlocks +
                " halls, min " + (int) (fewestWalls * 100) + "% walls, min " +
                worstMonN + " free neighbours per monster");
    }

    /** Экран карты продвижения: 45 узлов из ROM, 13 колонок, 4 концовки. */
    private static void testProgressChart() throws Exception {
        int[] rows = (int[]) field(Game.class, "ROWS").get(null);
        String[] nodes = (String[]) field(Game.class, "NODES").get(null);
        String[] endings = (String[]) field(Game.class, "ENDING_LABEL").get(null);
        check(rows.length == 13, "progress chart has 13 stage columns");
        check(endings.length == 4, "progress chart has 4 endings");
        int sum = 0;
        for (int i = 0; i < rows.length; i++) sum += rows[i];
        check(sum == 45, "progress chart has 45 rooms (ROM table minus DEMO)");
        check(nodes.length == 45, "45 four-letter ROM node names");
        boolean namesOk = true, unique = true;
        for (int c = 0; c < rows.length; c++) {
            for (int r = 0; r < rows[c]; r++) {
                String s = Game.nodeName(c, r);
                if (s.length() != 4 || !s.equals(s.toUpperCase())) namesOk = false;
            }
        }
        for (int i = 0; i < nodes.length; i++)
            for (int j = i + 1; j < nodes.length; j++)
                if (nodes[i].equals(nodes[j])) unique = false;
        check(namesOk, "every node name is 4 uppercase letters");
        check(unique, "ROM node names are unique");
        check("ROPE".equals(Game.nodeName(0, 0)), "chart starts at ROPE");
        check("DARK".equals(Game.nodeName(12, 0)), "chart ends at DARK");

        Method nr = method(Game.class, "nextRows", new Class[] {int.class, int.class});
        boolean edgesOk = true;
        for (int c = 0; c < rows.length - 1; c++) {
            for (int r = 0; r < rows[c]; r++) {
                int p = ((Integer) nr.invoke(null,
                        new Object[] {new Integer(c), new Integer(r)})).intValue();
                int a = (p >> 4) & 15, b = p & 15;
                if (a < 0 || a >= rows[c + 1] || b < 0 || b >= rows[c + 1])
                    edgesOk = false;
                if (a == b && rows[c + 1] > 1) edgesOk = false;
            }
        }
        check(edgesOk, "every branch points to two valid nodes");

        BufferedImage img = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics gfx = new Graphics(img);
        Game g = new Game(null);
        Field fState = field(Game.class, "state");
        tap(g, 53); tap(g, 53);                 // НОВЫЙ ЗАБЕГ -> PLAY
        Method finish = method(Game.class, "finishLevel", new Class[] {long.class});
        finish.invoke(g, new Object[] {new Long(System.currentTimeMillis())});
        check(fState.getInt(g) == 7, "clearing a floor opens PROGRESS chart");
        g.paint(gfx);
        g.paint(gfx);
        Renderer r = (Renderer) field(Game.class, "renderer").get(g);
        int red = 0;
        for (int i = 0; i < r.screen.length; i++) {
            int c = r.screen[i];
            if (((c >> 16) & 255) > 120 && ((c >> 8) & 255) < 110) red++;
        }
        check(red > 400, "progress chart draws the red/black tree (" + red + " px)");
        int[] route = (int[]) field(Game.class, "routeRow").get(g);
        check(route[1] >= 0 && route[1] < rows[1], "fast floor picks upper branch");
        check(field(Game.class, "routeFast").getBoolean(g), "route records fast floor");
        tap(g, 53);
        check(fState.getInt(g) == 2, "chart continues to floor briefing");
        tap(g, 53);
        check(fState.getInt(g) == 3, "briefing continues to PLAY");
        finish.invoke(g, new Object[] {new Long(System.currentTimeMillis())});
        tap(g, -7);
        check(fState.getInt(g) == 6, "right soft key pauses from chart");
        tap(g, -7);
        check(fState.getInt(g) == 7, "right soft key returns to chart");
        Save.clear();
        System.out.println("  progress chart: ok");
    }

    /** Плавный доворот: логика мгновенна, камера интерполируется по времени. */
    private static void testSmoothTurn() throws Exception {
        check(Fx.SUB == 16, "fine angle is 1/16 degree");
        check(Fx.sinFine(0) == 0, "sinFine(0)");
        check(Fx.cosFine(0) == 4096, "cosFine(0)");
        check(Fx.sinFine(90 * Fx.SUB) == 4096, "sinFine(90)");
        check(Fx.cosFine(90 * Fx.SUB) == 0, "cosFine(90)");
        int s45 = Fx.sinFine(45 * Fx.SUB + 8);
        int c45 = Fx.cosFine(45 * Fx.SUB + 8);
        check(s45 > Fx.sin(45) && s45 < Fx.sin(46),
                "sinFine interpolates inside a degree (" + s45 + ")");
        check(c45 < Fx.cos(45) && c45 > Fx.cos(46),
                "cosFine interpolates inside a degree (" + c45 + ")");
        check(Fx.sinFine(-90 * Fx.SUB) == -4096, "sinFine handles negative angles");

        BufferedImage img = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics gfx = new Graphics(img);
        Game g = new Game(null);
        Field fDir = field(Game.class, "dirDeg");
        Field fCam = field(Game.class, "camAngle");
        Field fCamDirX = field(Game.class, "camDirX");
        Field fDirX = field(Game.class, "dirX");
        Field fTurnStart = field(Game.class, "turnStart");
        tap(g, 53); tap(g, 53);                  // НОВЫЙ ЗАБЕГ -> PLAY
        int startDeg = fDir.getInt(g);
        check(fCam.getInt(g) == startDeg * Fx.SUB,
                "camera aligned with heading at level start");
        check(fCamDirX.getInt(g) == fDirX.getInt(g),
                "idle camera vectors equal logical vectors");

        tap(g, 54);                              // 6 = поворот вправо на 90
        check(fDir.getInt(g) == Fx.fixDegree(startDeg + 90),
                "logical heading still turns instantly by 90");
        Method adv = method(Game.class, "advanceCamera", new Class[] {long.class});
        long t0 = fTurnStart.getLong(g);
        int target = startDeg * Fx.SUB + 90 * Fx.SUB;
        adv.invoke(g, new Object[] {new Long(t0 + 57)});
        int mid = fCam.getInt(g);
        check(mid > startDeg * Fx.SUB && mid < target,
                "camera is between headings mid-turn (" + mid + ")");
        check(fCamDirX.getInt(g) != fDirX.getInt(g),
                "raycaster uses the interpolated direction");
        adv.invoke(g, new Object[] {new Long(t0 + 231)});
        check(fCam.getInt(g) == target,
                "camera settles exactly on the new heading");
        check(fCamDirX.getInt(g) == fDirX.getInt(g),
                "camera vectors realign with logic after the turn");
        tap(g, 52);                              // обратный поворот
        g.paint(gfx);
        g.paint(gfx);
        check(fDir.getInt(g) == startDeg, "second turn restores heading");
        System.out.println("  smooth turn: ok");
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

        tap(g, 53); // TITLE -> BRIEF (пункт «НОВЫЙ ЗАБЕГ»)
        check(fState.getInt(g) == 2, "state BRIEF after new game");
        tap(g, 53); // BRIEF -> PLAY
        check(fState.getInt(g) == 3, "state PLAY after start");

        for (int stage = 0; stage < 13; stage++) {
            Level lv = (Level) fLevel.get(g);
            fHealth.setInt(g, 5);
            // убираем тварей, чтобы они не перекрывали тестовый маршрут
            for (int i = 0; i < lv.monsterCount; i++) {
                lv.monsters[i].alive = false;
                lv.monsters[i].respawnAt = 0;
            }
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
            check(st == 7 || st == 4,
                    "stage " + stage + ": reached PROGRESS chart or ENDING, got " + st);
            if (st == 4) break;
            tap(g, 53); // PROGRESS -> BRIEF
            check(fState.getInt(g) == 2, "stage " + stage + ": chart leads to BRIEF");
            tap(g, 53); // BRIEF -> PLAY
        }
        check(fState.getInt(g) == 4, "full run reaches an ending");
        check(fStage.getInt(g) == 13, "full run clears 13 floors");
        check(fEnding.getInt(g) == Endings.A, "fast flawless run gives ending A");
        check(!Save.hasAuto(), "completed run clears autosave");
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
