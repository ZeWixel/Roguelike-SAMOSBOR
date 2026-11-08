package samosbor;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;

/**
 * ГИГАХРУЩЁВКА: САМОСБОР (J2ME / MIDP 2.0)
 *
 * Геймплей — по образцу Insmouse no Yakata (Virtual Boy, 1995):
 *  - вид от первого лица, рейкастинг, перемещение ПО КЛЕТКАМ, поворот на 90°;
 *  - два «D-pad»: левый — движение/поворот, правый — свободный прицел;
 *  - на каждом этаже: ключ-пропуск, гермодверь, жёсткий таймер до Самосбора;
 *  - белый шар открывает карту, чёрный показывает предметы;
 *  - 6 патронов в магазине, ручная перезарядка, сердечки/пищконцентрат;
 *  - 13 этажей из процедурно ветвящейся Гигахрущёвки, 4 концовки;
 *  - пароль из 4 символов вместо сохранения.
 */
public final class Game extends Canvas {

    private static final int FP = Fx.FP;

    // Состояния
    private static final int TITLE = 0, PASSWORD = 1, BRIEF = 2, PLAY = 3,
            HELP = 4, ENDING = 5, DEAD = 6;

    // Логические кнопки
    private static final int A_UP = 0, A_DOWN = 1, A_LEFT = 2, A_RIGHT = 3,
            A_FIRE = 4, A_AIM_LU = 5, A_AIM_RU = 6, A_AIM_LD = 7,
            A_AIM_RD = 8, A_MAP = 9, A_RELOAD = 10, A_VB = 11, A_COUNT = 12;

    // Цвета HUD
    private static final int C_WHITE = 0xfff0e6d0, C_RED = 0xffe05050,
            C_PURPLE = 0xffb952c7, C_YELLOW = 0xffd8cf40,
            C_GREEN = 0xff8ec06a, C_DARK = 0xff180e20,
            C_GREY = 0xff8d8e8e;

    private final Main midlet;
    private int w, h, viewH, hudH;
    private final Texture[] textures;
    private final Renderer renderer;
    private final boolean[] held = new boolean[A_COUNT];
    private final Raster[] titleMonsters = new Raster[3];

    private int K_UP = -1, K_DOWN = -2, K_LEFT = -3, K_RIGHT = -4, K_FIRE = -5;

    private int state = TITLE;
    private long stateSince, lastFrame, lastDamage, flashUntil, messageUntil,
            nextNav, nextDistCompute, nextRespawnCheck;
    private String message = "";
    private int messageColor = C_WHITE;
    private String deathReason = "";
    private boolean showMap;

    // Пробег
    private int runSeed, stage, floorNumber, deaths, fastFloors, totalTimeLeft,
            minFloor, ending;
    private boolean usedPassword;
    private String password = "AAAA";
    private char[] passChars = {'A', 'A', 'A', 'A'};
    private int passSlot;
    private Rng aiRng;

    // Уровень и игрок
    private Level level;
    private long timeLeftMs;
    private int playerTX, playerTY, posX, posY, dirDeg, dirX, dirY, planeX, planeY;
    private int aimX, aimY;
    private int health, magazine, reserve;
    private boolean hasKey, hasWhite, hasBlack;
    private int planeLen;

    // Кэш строк HUD (без аллокаций в игровом цикле)
    private String hudLine1 = "", hudLine2 = "", hudLine3 = "", hudLine4 = "", hudLine5 = "";
    private int hudSecond = -1;
    private boolean hudDirty = true;

    public Game(Main midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
        w = getWidth();
        h = getHeight();
        hudH = 56;
        viewH = h - hudH;
        if (viewH < 80) viewH = h - 30;
        if (w < 80) w = 80;

        detectKeypad();
        planeLen = -(3 * FP * w) / (4 * viewH);

        textures = new Texture[16];
        String[] names = {
            "beton.png", "kraska.png", "plitka.png", "germodver.png",
            "dver.png", "okno.png", "poster.png", "truby.png",
            "treshchina.png", "lift.png", "pol.png", "potolok.png",
            "sliz.png", "reshetka.png", "klet.png", "kirpich.png"
        };
        for (int i = 0; i < textures.length; i++) {
            textures[i] = Texture.load("/textures/" + names[i]);
        }
        renderer = new Renderer(w, h, viewH, textures);

        Rng r = new Rng(0x50A40C);
        for (int i = 0; i < titleMonsters.length; i++) {
            titleMonsters[i] = Monster.create(r, i).frames[0];
        }

        aimX = w >> 1;
        aimY = viewH >> 1;
        aiRng = new Rng(0x1234abcdL);
        stateSince = lastFrame = System.currentTimeMillis();
        repaint();
    }

    // -------------------------------------------------------------- клавиатура

    private boolean isSelect(int i) {
        try {
            return getKeyName(i).toUpperCase().indexOf("SELECT") != -1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Определение раскладки D-pad — как classes.Keyboard из Mecasting. */
    private void detectKeypad() {
        if (isSelect(-26)) {            // Siemens
            K_LEFT = -61; K_RIGHT = -62; K_DOWN = -60; K_UP = -59; K_FIRE = -26;
        } else if (isSelect(-20)) {     // Motorola
            K_LEFT = -2; K_RIGHT = -5; K_DOWN = -6; K_UP = -1; K_FIRE = -20;
        }
    }

    private int norm(int k) {
        if (k == K_UP || k == Canvas.UP || k == Canvas.KEY_NUM2 || k == '2') return A_UP;
        if (k == K_DOWN || k == Canvas.DOWN || k == Canvas.KEY_NUM8 || k == '8') return A_DOWN;
        if (k == K_LEFT || k == Canvas.LEFT || k == Canvas.KEY_NUM4 || k == '4') return A_LEFT;
        if (k == K_RIGHT || k == Canvas.RIGHT || k == Canvas.KEY_NUM6 || k == '6') return A_RIGHT;
        if (k == K_FIRE || k == Canvas.FIRE || k == Canvas.KEY_NUM5 || k == '5') return A_FIRE;
        if (k == Canvas.KEY_NUM1 || k == '1') return A_AIM_LU;
        if (k == Canvas.KEY_NUM3 || k == '3') return A_AIM_RU;
        if (k == Canvas.KEY_NUM7 || k == '7') return A_AIM_LD;
        if (k == Canvas.KEY_NUM9 || k == '9') return A_AIM_RD;
        if (k == Canvas.KEY_STAR || k == '*') return A_MAP;
        if (k == Canvas.KEY_NUM0 || k == '0') return A_RELOAD;
        if (k == Canvas.KEY_POUND || k == '#') return A_VB;
        try {
            int ga = getGameAction(k);
            if (ga == Canvas.UP) return A_UP;
            if (ga == Canvas.DOWN) return A_DOWN;
            if (ga == Canvas.LEFT) return A_LEFT;
            if (ga == Canvas.RIGHT) return A_RIGHT;
            if (ga == Canvas.FIRE) return A_FIRE;
        } catch (Exception e) {
        }
        return -1;
    }

    protected void keyPressed(int k) {
        int a = norm(k);
        if (a < 0) return;
        held[a] = true;
        press(a);
    }

    protected void keyReleased(int k) {
        int a = norm(k);
        if (a >= 0 && a < A_COUNT) held[a] = false;
    }

    protected void keyRepeated(int k) {
        // повтор движения обрабатывается своим таймером в update()
    }

    private void press(int a) {
        long now = System.currentTimeMillis();
        if (a == A_VB) {
            renderer.setVbMode(!renderer.vbMode);
            setMessage(renderer.vbMode ? "ОЧКИ VIRTUAL BOY" : "ОБЫЧНЫЙ ЭКРАН", C_PURPLE);
        }
        switch (state) {
            case TITLE:
                if (a == A_FIRE) newGame();
                else if (a == A_RELOAD) { state = PASSWORD; passSlot = 0; stateSince = now; }
                else if (a == A_AIM_RU || a == A_MAP) { state = HELP; stateSince = now; }
                break;
            case PASSWORD:
                if (a == A_UP) cyclePass(1);
                else if (a == A_DOWN) cyclePass(-1);
                else if (a == A_LEFT) { passSlot = (passSlot + 3) & 3; }
                else if (a == A_RIGHT) { passSlot = (passSlot + 1) & 3; }
                else if (a == A_MAP) { state = TITLE; stateSince = now; }
                else if (a == A_FIRE) {
                    String s = new String(passChars);
                    if (continueFromPassword(s)) setMessage("ПАРОЛЬ ПРИНЯТ", C_GREEN);
                    else setMessage("ПАРОЛЬ НЕВЕРЕН", C_RED);
                }
                break;
            case HELP:
                if (a == A_FIRE || a == A_MAP || a == A_RELOAD) { state = TITLE; stateSince = now; }
                break;
            case BRIEF:
                if (a == A_FIRE || a == A_MAP) { state = PLAY; stateSince = now; hudDirty = true; }
                break;
            case PLAY:
                if (a == A_FIRE) fire(now);
                else if (a == A_RELOAD) reload(now);
                else if (a == A_MAP) { showMap = !showMap; setMessage(showMap ? "КАРТА ЭТАЖА" : "", C_GREEN); }
                else if (a == A_UP || a == A_DOWN || a == A_LEFT || a == A_RIGHT) {
                    navigate(a, now);
                    nextNav = now + 230;
                }
                break;
            case ENDING:
            case DEAD:
                if (a == A_FIRE) newGame();
                else if (a == A_RELOAD) { state = PASSWORD; passSlot = 0; stateSince = now; }
                break;
            default:
                break;
        }
    }

    private void cyclePass(int d) {
        int i = Passwords.ALPHABET.indexOf(passChars[passSlot]);
        i = (i + d + Passwords.ALPHABET.length()) % Passwords.ALPHABET.length();
        passChars[passSlot] = Passwords.ALPHABET.charAt(i);
    }

    // ------------------------------------------------------------- игровой цикл

    protected void paint(Graphics g) {
        long now = System.currentTimeMillis();
        long dt = now - lastFrame;
        if (dt < 0) dt = 0;
        if (dt > 200) dt = 200;
        lastFrame = now;
        if (state == PLAY) update(dt, now);
        renderFrame(now);
        g.drawRGB(renderer.screen, 0, w, 0, 0, w, h, false);
        try {
            Thread.sleep(1L);
        } catch (Exception e) {
        }
        repaint();
    }

    private void update(long dt, long now) {
        // Правый D-pad: диагональные клавиши 1/3/7/9, пары дают прямое направление
        int ax = 0, ay = 0;
        if (held[A_AIM_LU]) { ax--; ay--; }
        if (held[A_AIM_RU]) { ax++; ay--; }
        if (held[A_AIM_LD]) { ax--; ay++; }
        if (held[A_AIM_RD]) { ax++; ay++; }
        if (ax != 0 || ay != 0) {
            int step = (int) (150 * dt / 1000);
            if (step < 1) step = 1;
            aimX += ax * step;
            aimY += ay * step;
            if (aimX < 2) aimX = 2;
            if (aimX > w - 3) aimX = w - 3;
            if (aimY < 2) aimY = 2;
            if (aimY > viewH - 3) aimY = viewH - 3;
        }
        // Левый D-pad: удержание = повтор шага
        if (now >= nextNav) {
            int a = -1;
            if (held[A_UP]) a = A_UP;
            else if (held[A_DOWN]) a = A_DOWN;
            else if (held[A_LEFT]) a = A_LEFT;
            else if (held[A_RIGHT]) a = A_RIGHT;
            if (a >= 0) {
                navigate(a, now);
                nextNav = now + 190;
            }
        }

        timeLeftMs -= dt;
        if (timeLeftMs <= 0) {
            deathReason = "САМОСБОР НАСТУПИЛ";
            die(now);
            return;
        }
        if ((int) (timeLeftMs / 1000) != hudSecond) hudDirty = true;
        updateMonsters(dt, now);
        if (messageUntil != 0 && now > messageUntil) {
            message = "";
            messageUntil = 0;
        }
    }

    private void updateMonsters(long dt, long now) {
        if (now >= nextDistCompute) {
            level.computeDist(playerTX, playerTY);
            nextDistCompute = now + 320;
        }
        for (int i = 0; i < level.monsterCount; i++) {
            Monster m = level.monsters[i];
            if (!m.alive) {
                if (m.respawnAt != 0 && now >= m.respawnAt && now >= nextRespawnCheck) {
                    int p = level.randomFloor(7);
                    m.place(p % level.w, p / level.w);
                    m.respawnAt = 0;
                    nextRespawnCheck = now + 1500;
                }
                continue;
            }
            m.advance((int) dt);
            if (!m.moving && now >= m.nextThink) {
                m.nextThink = now + m.speedMs / 2 + aiRng.nextInt(180);
                int cur = level.idx(m.tileX, m.tileY);
                int d = level.dist[cur];
                if (d >= 0 && d <= m.sight) {
                    int bestD = d, bx = m.tileX, by = m.tileY;
                    for (int k = 0; k < 4; k++) {
                        int nx = m.tileX + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                        int ny = m.tileY + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                        if (!level.inside(nx, ny)) continue;
                        if (level.isBreakable(nx, ny) && aiRng.chance(22)) {
                            if (level.breakWall(nx, ny)) {
                                level.computeDist(playerTX, playerTY);
                                nextDistCompute = now + 320;
                            }
                        }
                        if (level.isWall(nx, ny)) continue;
                        int nd = level.dist[level.idx(nx, ny)];
                        if (nd >= 0 && nd < bestD) { bestD = nd; bx = nx; by = ny; }
                    }
                    if (bx != m.tileX || by != m.tileY) {
                        m.toX = bx; m.toY = by; m.moveT = 0; m.moving = true;
                    }
                } else if (aiRng.chance(22)) {
                    int k = aiRng.nextInt(4);
                    int nx = m.tileX + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                    int ny = m.tileY + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                    if (!level.isWall(nx, ny)) {
                        m.toX = nx; m.toY = ny; m.moveT = 0; m.moving = true;
                    }
                }
            }
            int dx = m.tileX - playerTX; if (dx < 0) dx = -dx;
            int dy = m.tileY - playerTY; if (dy < 0) dy = -dy;
            if (dx + dy <= 1 && now >= m.nextAttack) {
                m.nextAttack = now + 950;
                damage(m.damage, now);
            }
        }
    }

    // ------------------------------------------------------------------ действия

    private void navigate(int a, long now) {
        if (showMap) return;
        if (a == A_LEFT) { rotate(-90); return; }
        if (a == A_RIGHT) { rotate(90); return; }
        int sign = (a == A_UP) ? 1 : -1;
        int tx = playerTX + (dirX * sign >> 12);
        int ty = playerTY + (dirY * sign >> 12);
        if (!level.inside(tx, ty)) return;
        int cell = level.idx(tx, ty);
        byte t = level.tile[cell];
        if (t == Level.EXIT) {
            if (hasKey) {
                finishLevel(now);
            } else {
                setMessage("ГЕРМОДВЕРЬ: НУЖЕН ПРОПУСК", C_YELLOW);
            }
            return;
        }
        if (t != Level.FLOOR) {
            setMessage("БЕТОН. НЕ ПРОЙТИ", C_GREY);
            return;
        }
        Monster blocker = monsterAt(tx, ty);
        if (blocker != null) {
            setMessage("ТВАРЬ ЗАКРЫЛА КОРИДОР", C_RED);
            damage(blocker.damage, now);
            return;
        }
        playerTX = tx;
        playerTY = ty;
        posX = tx * FP + FP / 2;
        posY = ty * FP + FP / 2;
        for (int yy = ty - 1; yy <= ty + 1; yy++) {
            for (int xx = tx - 1; xx <= tx + 1; xx++) level.markSeen(xx, yy);
        }
        int item = level.itemAt(tx, ty);
        if (item >= 0) pickup(level.itemType[item], item, now);
        level.computeDist(playerTX, playerTY);
    }

    private Monster monsterAt(int tx, int ty) {
        for (int i = 0; i < level.monsterCount; i++) {
            Monster m = level.monsters[i];
            if (!m.alive) continue;
            if (m.tileX == tx && m.tileY == ty) return m;
            if (m.moving && m.toX == tx && m.toY == ty) return m;
        }
        return null;
    }

    private void pickup(int type, int item, long now) {
        switch (type) {
            case Level.ITEM_KEY:
                hasKey = true;
                setMessage("ПРОПУСК ПАРТИИ: ИЩИ ГЕРМОДВЕРЬ", C_YELLOW);
                break;
            case Level.ITEM_WHITE:
                hasWhite = true;
                level.revealAll();
                setMessage("БЕЛЫЙ ШАР: КАРТА ЭТАЖА", C_WHITE);
                break;
            case Level.ITEM_BLACK:
                hasBlack = true;
                setMessage("ЧЁРНЫЙ ШАР: ВИДНЫ ПРЕДМЕТЫ", C_PURPLE);
                break;
            case Level.ITEM_AMMO:
                reserve += 6;
                setMessage("ПАТРОНЫ +6", C_YELLOW);
                break;
            case Level.ITEM_MED:
                if (health < 5) health++;
                setMessage("ПИЩКОНЦЕНТРАТ: +1 СЕРДЦЕ", C_GREEN);
                break;
            case Level.ITEM_CLOCK:
                timeLeftMs += 20000;
                setMessage("ТАЛОН: +20 СЕКУНД", C_GREEN);
                break;
            default:
                break;
        }
        hudDirty = true;
        level.removeItem(item);
    }

    private void fire(long now) {
        if (showMap) return;
        if (magazine <= 0) {
            setMessage("МАГАЗИН ПУСТ: КЛАВИША 0", C_YELLOW);
            return;
        }
        magazine--;
        hudDirty = true;
        flashUntil = now + 55;
        int best = -1, bestDepth = Integer.MAX_VALUE;
        for (int i = 0; i < renderer.sprCount; i++) {
            if (aimX >= renderer.sprLeft[i] && aimX < renderer.sprRight[i] &&
                    aimY >= renderer.sprTop[i] && aimY < renderer.sprBottom[i]) {
                if (renderer.sprDepth[i] < bestDepth) {
                    bestDepth = renderer.sprDepth[i];
                    best = renderer.sprIndex[i];
                }
            }
        }
        if (best < 0) {
            setMessage("МИМО", C_GREY);
            return;
        }
        Monster m = level.monsters[best];
        m.hp -= 2 + stage / 4;
        if (m.hp <= 0) {
            m.alive = false;
            m.respawnAt = now + 14000;
            setMessage("ТВАРЬ РАЗОБРАНА НА ЧАСТИ", C_GREEN);
            buzz(80);
        } else {
            setMessage("ПОПАДАНИЕ: " + m.name, C_YELLOW);
        }
    }

    private void reload(long now) {
        if (reserve <= 0) {
            setMessage("ПАТРОНОВ НЕТ", C_RED);
            return;
        }
        int need = 6 - magazine;
        int take = need < reserve ? need : reserve;
        magazine += take;
        reserve -= take;
        hudDirty = true;
        setMessage("ПЕРЕЗАРЯДКА +" + take, C_YELLOW);
    }

    private void damage(int dmg, long now) {
        if (now - lastDamage < 750) return;
        lastDamage = now;
        health -= dmg;
        hudDirty = true;
        flashUntil = now + 180;
        if (health <= 0) {
            deathReason = "ТВАРИ ДОСТАЛИ ЛИКВИДАТОРА";
            die(now);
        } else {
            setMessage("УРОН! СЕРДЕЦ: " + health, C_RED);
            buzz(120);
        }
    }

    private void die(long now) {
        deaths++;
        ending = Endings.DEAD;
        password = Passwords.encode(runSeed, stage, floorNumber);
        state = DEAD;
        stateSince = now;
        buzz(250);
    }

    private void finishLevel(long now) {
        int pct = (int) (100L * timeLeftMs / (level.timeLimitSec * 1000L));
        if (pct > 50) {
            floorNumber++;
            fastFloors++;
            totalTimeLeft += pct;
            setMessage("БЫСТРО: ЛИФТ ВВЕРХ", C_GREEN);
        } else {
            floorNumber--;
            setMessage("В ПОСЛЕДНЮЮ МИНУТУ: ВНИЗ", C_YELLOW);
        }
        if (floorNumber < 540) floorNumber = 540;
        if (floorNumber > 585) floorNumber = 585;
        if (floorNumber < minFloor) minFloor = floorNumber;
        stage++;
        if (stage >= 13) {
            ending = Endings.choose(deaths, usedPassword, totalTimeLeft,
                    magazine + reserve, fastFloors, minFloor);
            password = Passwords.encode(runSeed, stage, floorNumber);
            state = ENDING;
            stateSince = now;
            buzz(200);
        } else {
            buildLevel();
            state = BRIEF;
            stateSince = now;
        }
    }

    private void newGame() {
        runSeed = 0x1a2b000 | (int) (System.currentTimeMillis() & 1023);
        stage = 0;
        floorNumber = 556;
        deaths = 0;
        fastFloors = 0;
        totalTimeLeft = 0;
        minFloor = 556;
        usedPassword = false;
        deathReason = "";
        resetPlayer();
        buildLevel();
        showMap = false;
        state = BRIEF;
        stateSince = System.currentTimeMillis();
        aiRng = new Rng(runSeed ^ 0x5bf03635L);
    }

    private boolean continueFromPassword(String s) {
        int[] out = new int[3];
        if (!Passwords.decode(s, out)) return false;
        runSeed = out[0];
        stage = out[1];
        floorNumber = out[2];
        deaths = 0;
        fastFloors = 0;
        totalTimeLeft = 0;
        minFloor = floorNumber;
        usedPassword = true;
        deathReason = "";
        resetPlayer();
        buildLevel();
        showMap = false;
        state = BRIEF;
        stateSince = System.currentTimeMillis();
        aiRng = new Rng(runSeed ^ 0x5bf03635L);
        return true;
    }

    private void resetPlayer() {
        health = 3;
        magazine = 6;
        reserve = 6;
        hasKey = hasWhite = hasBlack = false;
        showMap = false;
        message = "";
        messageUntil = 0;
        aimX = w >> 1;
        aimY = viewH >> 1;
        dirDeg = 0;
        setVectors();
        hudDirty = true;
        lastDamage = 0;
    }

    private void buildLevel() {
        level = new Level(runSeed, floorNumber, stage);
        playerTX = level.startX;
        playerTY = level.startY;
        posX = playerTX * FP + FP / 2;
        posY = playerTY * FP + FP / 2;
        setVectors();
        level.computeDist(playerTX, playerTY);
        // Вход на этаж смотрит ВДОЛЬ коридора, а не в наружную стену.
        int bestD = -1, bestDeg = 0;
        int[] vx = {-1, 0, 1, 0};
        int[] vy = {0, -1, 0, 1};
        int[] deg = {0, 90, 180, 270};
        for (int k = 0; k < 4; k++) {
            int nx = playerTX + vx[k], ny = playerTY + vy[k];
            if (level.isWall(nx, ny)) continue;
            int d = level.dist[level.idx(nx, ny)];
            if (d > bestD) { bestD = d; bestDeg = deg[k]; }
        }
        dirDeg = bestDeg;
        setVectors();
        level.markSeen(playerTX, playerTY);
        timeLeftMs = level.timeLimitSec * 1000L;
        password = Passwords.encode(runSeed, stage, floorNumber);
        nextDistCompute = 0;
        nextRespawnCheck = 0;
        hudDirty = true;
        hudSecond = -1;
    }

    private void rotate(int delta) {
        dirDeg = Fx.fixDegree(dirDeg + delta);
        setVectors();
    }

    private void setVectors() {
        dirX = Fx.rotateXVector(-FP, 0, dirDeg);
        dirY = Fx.rotateYVector(-FP, 0, dirDeg);
        planeX = Fx.rotateXVector(0, planeLen, dirDeg);
        planeY = Fx.rotateYVector(0, planeLen, dirDeg);
    }

    private void setMessage(String s, int color) {
        message = s;
        messageColor = color;
        messageUntil = System.currentTimeMillis() + 1900;
    }

    private void buzz(int ms) {
        if (midlet == null) return;
        try {
            Display.getDisplay(midlet).vibrate(ms);
        } catch (Exception e) {
        }
    }

    // ------------------------------------------------------------------ отрисовка

    private void renderFrame(long now) {
        int[] buf = renderer.screen;
        fill(buf, 0xff0d0812);
        switch (state) {
            case TITLE: drawTitle(buf, now); break;
            case PASSWORD: drawPassword(buf); break;
            case BRIEF: drawBrief(buf); break;
            case HELP: drawHelp(buf); break;
            case PLAY:
                renderer.render(level, posX, posY, dirX, dirY, planeX, planeY, now);
                drawHud(buf, now);
                drawCrosshair(buf);
                if (showMap) drawMap(buf);
                else drawMessage(buf);
                if (now < flashUntil) drawFlash(buf, now);
                break;
            case ENDING:
            case DEAD:
                drawEnding(buf, now);
                break;
            default: break;
        }
    }

    private void fill(int[] buf, int c) {
        for (int i = 0; i < buf.length; i++) buf[i] = c;
    }

    private void box(int[] buf, int x, int y, int bw, int bh, int fill, int border) {
        for (int j = y; j < y + bh; j++) {
            if (j < 0 || j >= h) continue;
            int off = j * w;
            for (int i = x; i < x + bw; i++) {
                if (i < 0 || i >= w) continue;
                buf[off + i] = fill;
            }
        }
        hline(buf, x, x + bw - 1, y, border);
        hline(buf, x, x + bw - 1, y + bh - 1, border);
        vline(buf, x, y, y + bh - 1, border);
        vline(buf, x + bw - 1, y, y + bh - 1, border);
    }

    private void hline(int[] buf, int x0, int x1, int y, int c) {
        if (y < 0 || y >= h) return;
        if (x0 < 0) x0 = 0;
        if (x1 >= w) x1 = w - 1;
        int off = y * w;
        for (int x = x0; x <= x1; x++) buf[off + x] = c;
    }

    private void vline(int[] buf, int x, int y0, int y1, int c) {
        if (x < 0 || x >= w) return;
        if (y0 < 0) y0 = 0;
        if (y1 >= h) y1 = h - 1;
        for (int y = y0; y <= y1; y++) buf[y * w + x] = c;
    }

    private static int min(int a, int b) { return a < b ? a : b; }
    private static int max(int a, int b) { return a > b ? a : b; }

    private void blit(Raster spr, int x, int y, int scale) {
        int[] buf = renderer.screen;
        int[] p = (renderer.vbMode && spr.vb != null) ? spr.vb : spr.p;
        for (int j = 0; j < spr.h; j++) {
            for (int i = 0; i < spr.w; i++) {
                int c = p[j * spr.w + i];
                if ((c >>> 24) == 0) continue;
                box(buf, x + i * scale, y + j * scale, scale, scale, c, c);
            }
        }
    }

    private void center(String s, int y, int color, int scale) {
        int x = (w - BitmapFont.width(s, scale)) >> 1;
        if (x < 0) x = 0;
        BitmapFont.drawShadow(renderer.screen, w, h, s, x, y, color, scale);
    }

    private void drawTitle(int[] buf, long now) {
        // градиент + «бетонная» текстура фона
        for (int y = 0; y < h; y++) {
            int v = 12 + (y * 22) / h;
            int c = 0xff000000 | (v << 16) | ((v / 2) << 8) | (v + 12);
            hline(buf, 0, w - 1, y, c);
        }
        for (int i = 0; i < 120; i++) {
            int x = (i * 73) % w;
            int y = (i * 41) % h;
            buf[y * w + x] = 0xff3a2048;
        }
        int y = 12;
        center("ГИГАХРУЩЁВКА", y, C_PURPLE, 2);
        y += 20;
        center("САМОСБОР: ЛИКВИДАТОР", y, C_WHITE, 1);
        y += 12;
        center("J2ME / MIDP 2.0 / 3D ОТ ПЕРВОГО ЛИЦА", y, C_GREY, 1);

        for (int i = 0; i < titleMonsters.length; i++) {
            int bx = 14 + i * (w / 3);
            int by = h - 128 + ((i & 1) == 0 ? 0 : 6);
            blit(titleMonsters[i], bx, by, 2);
        }
        box(buf, 6, h - 76, w - 12, 70, 0xff140b1c, C_PURPLE);
        int my = h - 70;
        BitmapFont.drawShadow(buf, w, h, "5  НОВЫЙ ЗАБЕГ", 14, my, C_YELLOW, 1);
        BitmapFont.drawShadow(buf, w, h, "0  ВВЕСТИ ПАРОЛЬ", 14, my + 12, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "3  УПРАВЛЕНИЕ", 14, my + 24, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "#  ОЧКИ VIRTUAL BOY", 14, my + 36, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, "13 ЭТАЖЕЙ / 4 КОНЦОВКИ", 14, my + 50, C_PURPLE, 1);
    }

    private void drawPassword(int[] buf) {
        fill(buf, 0xff140b1c);
        center("ПАРОЛЬ ЭТАЖА", 18, C_PURPLE, 2);
        center("КАК В INSMOUSE NO YAKATA", 44, C_GREY, 1);
        box(buf, 10, 62, w - 20, 74, 0xff0d0812, C_PURPLE);
        for (int i = 0; i < 4; i++) {
            int x = 20 + i * ((w - 40) / 4);
            int c = i == passSlot ? C_YELLOW : C_GREY;
            box(buf, x, 76, 34, 44, i == passSlot ? 0xff3a2048 : 0xff1a1022, c);
            String s = new String(new char[]{passChars[i]});
            BitmapFont.drawShadow(buf, w, h, s, x + 10, 88, C_WHITE, 2);
        }
        center("2/8 БУКВА   4/6 СЛОТ", 146, C_WHITE, 1);
        center("5 ПРИНЯТЬ   * НАЗАД", 160, C_WHITE, 1);
        center("ПАРОЛЬ ВЫДАЁТСЯ НА БРИФИНГЕ", 178, C_GREY, 1);
        center(message, 200, messageColor, 1);
    }

    private void drawBrief(int[] buf) {
        fill(buf, 0xff120a1a);
        for (int y = 0; y < h; y += 8) hline(buf, 0, w - 1, y, 0xff1c1026);
        center("ЭТАЖ " + floorNumber, 16, C_PURPLE, 2);
        center("ЭТАП " + (stage + 1) + " ИЗ 13", 44, C_WHITE, 1);
        int sec = (int) (level.timeLimitSec);
        center("ДО САМОСБОРА: " + two(sec / 60) + ":" + two(sec % 60), 62, C_YELLOW, 1);
        box(buf, 8, 82, w - 16, 88, 0xff0d0812, C_PURPLE);
        int y = 90;
        BitmapFont.drawShadow(buf, w, h, "ЗАДАЧА: НАЙТИ ПРОПУСК", 14, y, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "И ОТКРЫТЬ ГЕРМОДВЕРЬ", 14, y + 12, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "БЕЛЫЙ ШАР - КАРТА", 14, y + 28, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, "ЧЁРНЫЙ ШАР - ПРЕДМЕТЫ", 14, y + 40, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, "ТВАРИ СОБРАНЫ ИЗ ЧАСТЕЙ ТЕЛА", 14, y + 56, C_PURPLE, 1);
        center("ПАРОЛЬ: " + password, 180, C_GREEN, 1);
        center("5  НАЧАТЬ ЗАБЕГ", 204, C_YELLOW, 1);
    }

    private void drawHelp(int[] buf) {
        fill(buf, 0xff120a1a);
        center("УПРАВЛЕНИЕ", 10, C_PURPLE, 2);
        int y = 38;
        String[] lines = {
            "ЛЕВЫЙ D-PAD (ХОДЬБА)",
            "2 ВПЕРЁД НА 1 КЛЕТКУ",
            "8 НАЗАД НА 1 КЛЕТКУ",
            "4 ПОВОРОТ ВЛЕВО 90",
            "6 ПОВОРОТ ВПРАВО 90",
            "",
            "ПРАВЫЙ D-PAD (ПРИЦЕЛ)",
            "1 ВВЕРХ-ВЛЕВО  3 ВВЕРХ-ВПРАВО",
            "7 ВНИЗ-ВЛЕВО   9 ВНИЗ-ВПРАВО",
            "1+3 ВВЕРХ  7+9 ВНИЗ",
            "1+7 ВЛЕВО 3+9 ВПРАВО",
            "",
            "5 ОГОНЬ   0 ПЕРЕЗАРЯДКА",
            "* КАРТА   # ОЧКИ VIRTUAL BOY",
            "",
            "ЦЕЛЬ: ПРОПУСК + ГЕРМОДВЕРЬ",
            "ДО НАЧАЛА САМОСБОРА",
            "5 ИЛИ * - НАЗАД"
        };
        for (int i = 0; i < lines.length; i++) {
            int c = (i == 0 || i == 6) ? C_YELLOW : C_WHITE;
            BitmapFont.drawShadow(buf, w, h, lines[i], 6, y + i * 11, c, 1);
        }
    }

    private void drawEnding(int[] buf, long now) {
        fill(buf, 0xff0d0812);
        for (int i = 0; i < 3; i++) {
            int t = (int) (now / 700 + i);
            if ((t & 1) == 0) blit(titleMonsters[i], 8 + i * (w / 3), h - 92, 2);
        }
        String[] lines = Endings.lines(ending);
        int y = 10;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() == 0) continue;
            int c = i == 0 ? C_PURPLE : (i == 1 ? C_YELLOW : C_WHITE);
            center(lines[i], y + i * 12, c, 1);
        }
        if (state == DEAD && deathReason.length() > 0) {
            center(deathReason, 112, C_RED, 1);
        }
        box(buf, 6, h - 104, w - 12, 54, 0xff140b1c, C_PURPLE);
        int by = h - 98;
        BitmapFont.drawShadow(buf, w, h, "ЭТАП " + (stage + 1) + "/13  ЭТАЖ " + floorNumber, 12, by, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "БЫСТРЫХ ЭТАЖЕЙ: " + fastFloors, 12, by + 12, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, "ПАРОЛЬ: " + password, 12, by + 24, C_GREEN, 1);
        BitmapFont.drawShadow(buf, w, h, "5 НОВЫЙ   0 ПАРОЛЬ", 12, by + 38, C_YELLOW, 1);
    }

    // ---------------------------------------------------------------------- HUD

    private static String two(int v) {
        if (v < 10) return "0" + v;
        return "" + v;
    }

    private void rebuildHud() {
        int sec = (int) (timeLeftMs / 1000);
        if (sec < 0) sec = 0;
        if (!hudDirty && sec == hudSecond) return;
        hudSecond = sec;
        hudDirty = false;
        StringBuffer sb = new StringBuffer();
        sb.append("ЭТАЖ "); sb.append(floorNumber);
        sb.append("  САМОСБОР "); sb.append(two(sec / 60)); sb.append(":"); sb.append(two(sec % 60));
        hudLine1 = sb.toString();
        StringBuffer s2 = new StringBuffer();
        s2.append("ПАТР "); s2.append(magazine); s2.append("/"); s2.append(reserve);
        s2.append(hasKey ? "  ~ПРОПУСК" : "  ~НЕТ");
        hudLine2 = s2.toString();
        hudLine3 = "2 ШАГ  8 НАЗАД  4/6 ПОВОРОТ 90";
        hudLine4 = "1/3/7/9 ПРИЦЕЛ  5 ОГОНЬ  0 ЗАРЯД";
        StringBuffer s5 = new StringBuffer();
        s5.append("* КАРТА  # VB  ЭТАП "); s5.append(stage + 1); s5.append("/13");
        if (hasWhite) s5.append("  W");
        if (hasBlack) s5.append("  B");
        hudLine5 = s5.toString();
    }

    private void drawHud(int[] buf, long now) {
        box(buf, 0, viewH, w, hudH, C_DARK, C_PURPLE);
        rebuildHud();
        int y = viewH + 4;
        int col = timeLeftMs < 30000 ? C_RED : C_WHITE;
        BitmapFont.drawShadow(buf, w, h, hudLine1, 4, y, col, 1);
        for (int i = 0; i < 5; i++) {
            BitmapFont.drawShadow(buf, w, h, "^", 4 + i * 8, y + 11,
                    i < health ? C_RED : 0xff3a2048, 1);
        }
        BitmapFont.drawShadow(buf, w, h, hudLine2, 46, y + 11, C_YELLOW, 1);
        BitmapFont.drawShadow(buf, w, h, hudLine3, 4, y + 22, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, hudLine4, 4, y + 32, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, hudLine5, 4, y + 42, C_PURPLE, 1);
    }

    private void drawCrosshair(int[] buf) {
        if (showMap) return;
        int c = C_RED;
        for (int i = 0; i < renderer.sprCount; i++) {
            if (aimX >= renderer.sprLeft[i] && aimX < renderer.sprRight[i] &&
                    aimY >= renderer.sprTop[i] && aimY < renderer.sprBottom[i]) {
                c = C_YELLOW;
                break;
            }
        }
        hline(buf, aimX - 9, aimX - 4, aimY, c);
        hline(buf, aimX + 4, aimX + 9, aimY, c);
        vline(buf, aimX, aimY - 9, aimY - 4, c);
        vline(buf, aimX, aimY + 4, aimY + 9, c);
        box(buf, aimX - 1, aimY - 1, 3, 3, 0xff000000, C_WHITE);
    }

    private void drawMessage(int[] buf) {
        if (message.length() == 0) return;
        int bw = BitmapFont.width(message, 1) + 12;
        if (bw > w - 8) bw = w - 8;
        box(buf, (w - bw) >> 1, 5, bw, 18, 0xff140b1c, C_PURPLE);
        BitmapFont.drawShadow(buf, w, h, message, ((w - bw) >> 1) + 6, 9, messageColor, 1);
    }

    private void drawFlash(int[] buf, long now) {
        int c = 0xffff3030;
        for (int y = 0; y < viewH; y += 3) {
            hline(buf, 0, w - 1, y, 0xff301010);
        }
        hline(buf, 0, w - 1, 0, c);
        hline(buf, 0, w - 1, viewH - 1, c);
        vline(buf, 0, 0, viewH - 1, c);
        vline(buf, w - 1, 0, viewH - 1, c);
    }

    // --------------------------------------------------------------------- карта

    private void drawMap(int[] buf) {
        fill(buf, 0xff0b0610);
        center("КАРТА ЭТАЖА " + floorNumber, 5, C_PURPLE, 1);
        int cs = min((w - 10) / level.w, (h - 76) / level.h);
        if (cs < 2) cs = 2;
        int ox = (w - cs * level.w) >> 1;
        int oy = 26 + ((h - 76 - cs * level.h) >> 1);
        if (oy < 24) oy = 24;
        box(buf, ox - 2, oy - 2, level.w * cs + 4, level.h * cs + 4,
                0xff0b0610, C_PURPLE);
        for (int y = 0; y < level.h; y++) {
            for (int x = 0; x < level.w; x++) {
                int i = level.idx(x, y);
                int c;
                if (!level.seen[i]) c = 0xff1a1024;
                else if (level.tile[i] == Level.FLOOR) c = 0xff50505a;
                else if (level.tile[i] == Level.EXIT) c = 0xffd8cf40;
                else if (level.tile[i] == Level.CRACKED) c = 0xff8b2c9b;
                else c = 0xff24202c;
                box(buf, ox + x * cs, oy + y * cs, cs, cs, c, 0xff0b0610);
                if (level.seen[i] && (hasBlack || hasWhite)) {
                    for (int k = 0; k < level.itemCount; k++) {
                        if (level.itemX[k] != x || level.itemY[k] != y) continue;
                        int ic = level.itemType[k] == Level.ITEM_KEY ? C_YELLOW : C_GREEN;
                        box(buf, ox + x * cs + cs / 3, oy + y * cs + cs / 3,
                                max(2, cs / 3), max(2, cs / 3), ic, ic);
                    }
                }
            }
        }
        // гермодверь и пропуск видны после чёрного шара
        if (hasBlack) {
            box(buf, ox + level.keyX * cs, oy + level.keyY * cs, cs, cs, C_YELLOW, C_YELLOW);
            box(buf, ox + level.exitX * cs, oy + level.exitY * cs, cs, cs, C_RED, C_RED);
        }
        // игрок и направление
        int px = ox + playerTX * cs, py = oy + playerTY * cs;
        box(buf, px, py, cs, cs, C_RED, C_WHITE);
        int vx = dirX >> 12, vy = dirY >> 12;
        int cx = px + cs / 2, cy = py + cs / 2;
        int ex = cx + vx * cs * 2, ey = cy + vy * cs * 2;
        hline(buf, min(cx, ex), max(cx, ex), cy, C_RED);
        vline(buf, ex, min(cy, ey), max(cy, ey), C_RED);

        int sec = (int) (timeLeftMs / 1000);
        center("САМОСБОР " + two(sec / 60) + ":" + two(sec % 60) +
                (hasKey ? "  ПРОПУСК ЕСТЬ" : "  ПРОПУСКА НЕТ"), h - 30, C_YELLOW, 1);
        center("* ВЕРНУТЬСЯ   ЭТАП " + (stage + 1) + "/13", h - 16, C_GREY, 1);
    }
}
