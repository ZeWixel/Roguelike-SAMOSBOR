package samosbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;

/**
 * ГИГАХРУЩЁВКА: САМОСБОР (J2ME / MIDP 2.0)
 *
 * Геймплей — по образцу Insmouse no Yakata (Virtual Boy, 1995):
 * вид от первого лица, перемещение по клеткам, поворот 90°, ключ-пропуск,
 * гермодверь, таймер Самосбора, белый/чёрный шары, редкие патроны, 13 этажей,
 * 4 концовки. Пароли заменены автосохранением в RMS.
 *
 * Управление:
 *   2/8/4/6  — шаг/поворот (или прицел, если оружие в руках)
 *   7        — достать/убрать оружие и перейти в режим прицеливания
 *   5        — выстрел (только с оружием), 0 — перезарядка
 *   *        — очки Virtual Boy, # — карта
 *   в меню 2/8 — перелистывание пунктов, 5 — выбор, 4/6 — значение
 */
public final class Game extends Canvas {

    private static final int FP = Fx.FP;

    private static final int TITLE = 0, HELP = 1, BRIEF = 2, PLAY = 3,
            ENDING = 4, DEAD = 5, PAUSE = 6;

    private static final int A_UP = 0, A_DOWN = 1, A_LEFT = 2, A_RIGHT = 3,
            A_FIRE = 4, A_AIM = 5, A_MAP = 6, A_VB = 7, A_RELOAD = 8,
            A_OPT_L = 9, A_OPT_R = 10, A_BACK = 11, A_PAUSE = 12, A_COUNT = 13;

    private static final int SAVE_MAGIC = 0x47485332; // "GHS2"
    private static final int SAVE_VERSION = 4;
    private static final int AUTOSAVE_MS = 12000;
    private static final int DAMAGE_COOLDOWN = 2600;
    private static final int SIREN_MS = 30000;

    private static final int C_WHITE = 0xfff0e6d0, C_RED = 0xffe05050,
            C_PURPLE = 0xffb952c7, C_YELLOW = 0xffd8cf40,
            C_GREEN = 0xff8ec06a, C_DARK = 0xff180e20,
            C_GREY = 0xff8d8e8e;

    private static final String[] MENU = {
        "НОВЫЙ ЗАБЕГ", "ПРОДОЛЖИТЬ", "УПРАВЛЕНИЕ", "МУЗЫКА: ВКЛ", "ВЫХОД"
    };

    private static final String[] PAUSE_MENU = {
        "ПРОДОЛЖИТЬ", "СОХРАНИТЬ СЕЙЧАС", "МУЗЫКА: ВКЛ",
        "УПРАВЛЕНИЕ", "ГЛАВНОЕ МЕНЮ"
    };

    private final Main midlet;
    private int w, h, viewH, hudH;
    private final Texture[] textures;
    private final Renderer renderer;
    private final boolean[] held = new boolean[A_COUNT];
    private final Raster[] titleMonsters = new Raster[3];
    private final Raster weaponSprite;
    private Audio audio;

    private int K_UP = -1, K_DOWN = -2, K_LEFT = -3, K_RIGHT = -4, K_FIRE = -5;
    // Правая софт-клавиша: Nokia/SE = -7, Motorola = -22, Siemens ≈ -11.
    private int K_SOFT_RIGHT = -7;

    private int state = TITLE;
    private int menuIndex, endMenuIndex, pauseMenuIndex;
    private int pauseReturn = PLAY, helpReturn = TITLE;
    private String pauseNote = "";
    private long pauseNoteUntil;
    private long stateSince, lastFrame, lastDamage, flashUntil, muzzleUntil,
            lastShotAt, messageUntil, nextNav, nextDistCompute, nextRespawnCheck,
            lastSaveAt;
    private String message = "";
    private int messageColor = C_WHITE;
    private String deathReason = "";
    private boolean showMap, aiming, musicOn = true, sirenDone, hasSave;

    private int runSeed, stage, floorNumber, deaths, fastFloors, totalTimeLeft,
            minFloor, ending;

    private Level level;
    private long timeLeftMs;
    private int playerTX, playerTY, posX, posY, dirDeg, dirX, dirY, planeX, planeY;
    private int aimX, aimY;
    private int health, magazine, reserve;
    private boolean hasKey, hasWhite, hasBlack;
    private int planeLen;

    private String hudLine1 = "", hudLine2 = "", hudLine3 = "", hudLine4 = "", hudLine5 = "";
    private int hudSecond = -1;
    private boolean hudDirty = true;
    private Rng aiRng;

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
        detectSoftKeys();
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
        weaponSprite = makeWeapon();

        try {
            audio = new Audio();
        } catch (Throwable t) {
            audio = null; // телефона без MMAPI — игра без звука
        }

        aimX = w >> 1;
        aimY = viewH >> 1;
        aiRng = new Rng(0x1234abcdL);
        hasSave = Save.hasAuto();
        music(0);
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

    private void detectKeypad() {
        if (isSelect(-26)) {
            K_LEFT = -61; K_RIGHT = -62; K_DOWN = -60; K_UP = -59; K_FIRE = -26;
        } else if (isSelect(-20)) {
            K_LEFT = -2; K_RIGHT = -5; K_DOWN = -6; K_UP = -1; K_FIRE = -20;
        }
    }

    /** Ищем правую софт-клавишу по имени (на разных вендоров разные коды). */
    private void detectSoftKeys() {
        for (int k = -1; k >= -32; k--) {
            if (k == K_LEFT || k == K_RIGHT || k == K_UP || k == K_DOWN ||
                    k == K_FIRE) continue;
            String n = getKeyName(k).toUpperCase();
            if (n.indexOf("SOFT") >= 0 && n.indexOf("RIGHT") >= 0) {
                K_SOFT_RIGHT = k;
                return;
            }
            if (n.indexOf("RIGHT") >= 0 && n.indexOf("KEY") >= 0) {
                K_SOFT_RIGHT = k;
                return;
            }
        }
        // Эвристика по вендорам, если getKeyName() ничего не вернул.
        int[] candidates = {-7, -22, -20, -11, -10};
        for (int i = 0; i < candidates.length; i++) {
            String n = getKeyName(candidates[i]).toUpperCase();
            if (n.length() > 0 && n.indexOf("LEFT") < 0) {
                K_SOFT_RIGHT = candidates[i];
                return;
            }
        }
    }

    private int norm(int k) {
        if (k == K_UP || k == Canvas.UP || k == Canvas.KEY_NUM2 || k == '2') return A_UP;
        if (k == K_DOWN || k == Canvas.DOWN || k == Canvas.KEY_NUM8 || k == '8') return A_DOWN;
        if (k == K_LEFT || k == Canvas.LEFT || k == Canvas.KEY_NUM4 || k == '4') return A_LEFT;
        if (k == K_RIGHT || k == Canvas.RIGHT || k == Canvas.KEY_NUM6 || k == '6') return A_RIGHT;
        if (k == K_FIRE || k == Canvas.FIRE || k == Canvas.KEY_NUM5 || k == '5') return A_FIRE;
        if (k == Canvas.KEY_NUM7 || k == '7') return A_AIM;
        if (k == Canvas.KEY_POUND || k == '#') return A_MAP;
        if (k == Canvas.KEY_STAR || k == '*') return A_VB;
        if (k == Canvas.KEY_NUM0 || k == '0') return A_RELOAD;
        if (k == Canvas.KEY_NUM1 || k == '1') return A_OPT_L;
        if (k == Canvas.KEY_NUM3 || k == '3') return A_OPT_R;
        if (k == Canvas.KEY_NUM9 || k == '9') return A_BACK;
        // Правая софт-клавиша — меню паузы. Литерал -7 и вендорские коды
        // проверяем явно: MIDP не определяет константу KEY_SOFT_RIGHT.
        if (k == -7 || k == -22 || k == -20 || k == -11 || k == -10 ||
                k == K_SOFT_RIGHT) return A_PAUSE;
        try {
            String kn = getKeyName(k).toUpperCase();
            if (kn.indexOf("SOFT") >= 0 && kn.indexOf("RIGHT") >= 0) return A_PAUSE;
        } catch (Exception e) {
        }
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
        // повтор реализован своим таймером в update()
    }

    protected void hideNotify() {
        autosave();
    }

    public void onPause() {
        autosave();
    }

    private void press(int a) {
        long now = System.currentTimeMillis();
        if (a == A_VB) {
            renderer.setVbMode(!renderer.vbMode);
            play(Audio.MENU);
            setMessage(renderer.vbMode ? "ОЧКИ VIRTUAL BOY" : "ОБЫЧНЫЙ ЭКРАН", C_PURPLE);
            return;
        }
        if (a == A_PAUSE) {
            togglePause(now);
            return;
        }
        switch (state) {
            case TITLE:
                if (a == A_UP) {
                    menuIndex = (menuIndex + MENU.length - 1) % MENU.length;
                    play(Audio.MENU);
                } else if (a == A_DOWN) {
                    menuIndex = (menuIndex + 1) % MENU.length;
                    play(Audio.MENU);
                } else if (a == A_LEFT || a == A_RIGHT || a == A_OPT_L || a == A_OPT_R) {
                    if (menuIndex == 3) toggleMusic();
                    else play(Audio.MENU);
                } else if (a == A_FIRE) {
                    selectMenu(now);
                }
                break;
            case HELP:
                if (a == A_FIRE || a == A_BACK) {
                    state = helpReturn;
                    stateSince = now;
                    play(Audio.MENU);
                }
                break;
            case BRIEF:
                if (a == A_FIRE) {
                    state = PLAY;
                    stateSince = now;
                    hudDirty = true;
                    play(Audio.DOOR);
                } else if (a == A_BACK) {
                    state = TITLE;
                    stateSince = now;
                }
                break;
            case PLAY:
                if (a == A_AIM) {
                    aiming = !aiming;
                    if (!aiming) {
                        // Как в оригинале: убрал оружие — прицел вернулся в центр.
                        aimX = w >> 1;
                        aimY = viewH >> 1;
                    }
                    hudDirty = true;
                    play(Audio.DRAW);
                    setMessage(aiming ? "ОРУЖИЕ ГОТОВО: ПРИЦЕЛ" : "ОРУЖИЕ УБРАНО: ПРИЦЕЛ В ЦЕНТРЕ", C_YELLOW);
                } else if (a == A_FIRE) {
                    if (aiming) fire(now);
                    else setMessage("СНАЧАЛА 7 - ДОСТАТЬ ОРУЖИЕ", C_YELLOW);
                } else if (a == A_RELOAD) {
                    reload(now);
                } else if (a == A_MAP) {
                    showMap = !showMap;
                    play(Audio.MENU);
                    setMessage(showMap ? "КАРТА ЭТАЖА" : "", C_GREEN);
                } else if (a == A_UP || a == A_DOWN || a == A_LEFT || a == A_RIGHT) {
                    if (!aiming) {
                        navigate(a, now);
                        nextNav = now + 230;
                    }
                }
                break;
            case ENDING:
            case DEAD:
                int n = endMenuCount();
                if (a == A_UP) {
                    endMenuIndex = (endMenuIndex + n - 1) % n;
                    play(Audio.MENU);
                } else if (a == A_DOWN) {
                    endMenuIndex = (endMenuIndex + 1) % n;
                    play(Audio.MENU);
                } else if (a == A_FIRE) {
                    selectEndMenu(now);
                }
                break;
            case PAUSE:
                if (a == A_UP) {
                    pauseMenuIndex = (pauseMenuIndex + PAUSE_MENU.length - 1) % PAUSE_MENU.length;
                    play(Audio.MENU);
                } else if (a == A_DOWN) {
                    pauseMenuIndex = (pauseMenuIndex + 1) % PAUSE_MENU.length;
                    play(Audio.MENU);
                } else if (a == A_LEFT || a == A_RIGHT || a == A_OPT_L || a == A_OPT_R) {
                    if (pauseMenuIndex == 2) toggleMusic();
                    else play(Audio.MENU);
                } else if (a == A_FIRE) {
                    selectPauseMenu(now);
                }
                break;
            default:
                break;
        }
    }

    private void togglePause(long now) {
        if (state == PLAY || state == BRIEF) {
            pauseReturn = state;
            state = PAUSE;
            pauseMenuIndex = 0;
            pauseNote = "";
            stateSince = now;
            autosave();              // пауза = гарантированный автосейв
            play(Audio.MENU);
        } else if (state == PAUSE) {
            state = pauseReturn;
            stateSince = now;
            lastFrame = now;
            play(Audio.MENU);
        } else if (state == HELP) {
            state = helpReturn;
            stateSince = now;
        }
    }

    private void selectPauseMenu(long now) {
        play(Audio.MENU);
        switch (pauseMenuIndex) {
            case 0:
                state = pauseReturn;
                stateSince = now;
                lastFrame = now;
                break;
            case 1:
                autosave();
                pauseNote = "АВТОСЕЙВ ОБНОВЛЁН";
                pauseNoteUntil = now + 1900;
                break;
            case 2:
                toggleMusic();
                break;
            case 3:
                helpReturn = PAUSE;
                state = HELP;
                stateSince = now;
                break;
            default:
                autosave();
                goTitle();
                break;
        }
    }

    private void selectMenu(long now) {
        switch (menuIndex) {
            case 0:
                newGame();
                play(Audio.DOOR);
                break;
            case 1:
                if (Save.hasAuto() && continueGame()) play(Audio.DOOR);
                else setMessage("СОХРАНЕНИЯ НЕТ", C_RED);
                break;
            case 2:
                helpReturn = TITLE;
                state = HELP;
                stateSince = now;
                play(Audio.MENU);
                break;
            case 3:
                toggleMusic();
                break;
            default:
                if (midlet != null) midlet.notifyDestroyed();
                else { state = TITLE; menuIndex = 0; }
                break;
        }
    }

    private void toggleMusic() {
        musicOn = !musicOn;
        if (audio != null) audio.setEnabled(musicOn);
        if (musicOn) music(0);
        else music(-1);
        play(Audio.MENU);
        setMessage(musicOn ? "ЗВУК ВКЛЮЧЁН" : "ЗВУК ВЫКЛЮЧЕН", C_YELLOW);
    }

    private int endMenuCount() {
        return state == DEAD ? 3 : 2;
    }

    private void selectEndMenu(long now) {
        if (state == DEAD) {
            if (endMenuIndex == 0) newGame();
            else if (endMenuIndex == 1) {
                if (!continueGame()) setMessage("СОХРАНЕНИЯ НЕТ", C_RED);
            } else goTitle();
        } else {
            if (endMenuIndex == 0) newGame();
            else goTitle();
        }
        play(Audio.MENU);
    }

    private void goTitle() {
        state = TITLE;
        menuIndex = 0;
        helpReturn = TITLE;
        pauseReturn = PLAY;
        pauseNote = "";
        hasSave = Save.hasAuto();
        music(0);
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
        if (aiming && !showMap) {
            // Правый «D-pad» на кнопках 2/4/6/8: прицел двигается непрерывно.
            int ax = 0, ay = 0;
            if (held[A_UP]) ay--;
            if (held[A_DOWN]) ay++;
            if (held[A_LEFT]) ax--;
            if (held[A_RIGHT]) ax++;
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
        } else if (now >= nextNav) {
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

        // В оригинале экран карты (Select) ОСТАНАВЛИВАЕТ таймер и мир.
        if (!showMap) {
            timeLeftMs -= dt;
            if (timeLeftMs <= 0) {
                deathReason = "САМОСБОР НАСТУПИЛ";
                die(now);
                return;
            }
            if (!sirenDone && timeLeftMs < SIREN_MS) {
                sirenDone = true;
                play(Audio.SIREN);
                music(1);
                setMessage("СИРЕНА! САМОСБОР ЧЕРЕЗ 30", C_RED);
            }
            updateMonsters(dt, now);
        }
        if ((int) (timeLeftMs / 1000) != hudSecond) hudDirty = true;
        if (now - lastSaveAt >= AUTOSAVE_MS) autosave();
        if (messageUntil != 0 && now > messageUntil) {
            message = "";
            messageUntil = 0;
        }
    }

    /**
     * ИИ как в Insmouse no Yakata: твари медленно бродят возле своего места,
     * замечают игрока только в прямой видимости на 3–5 клеток, перед укусом
     * замирают в позе выпада (0.8 с), снимают ровно ОДНО сердце и после
     * попадания шарахаются в сторону. Никакой погони через весь этаж.
     */
    private void updateMonsters(long dt, long now) {
        if (now >= nextDistCompute) {
            level.computeDist(playerTX, playerTY);
            nextDistCompute = now + 600;
        }
        for (int i = 0; i < level.monsterCount; i++) {
            Monster m = level.monsters[i];
            if (!m.alive) {
                if (m.respawnAt != 0 && now >= m.respawnAt && now >= nextRespawnCheck) {
                    int p = level.randomFloor(7);
                    m.place(p % level.w, p / level.w);
                    m.respawnAt = 0;
                    nextRespawnCheck = now + 2000;
                }
                continue;
            }
            m.advance((int) dt);

            int dx = m.tileX - playerTX; if (dx < 0) dx = -dx;
            int dy = m.tileY - playerTY; if (dy < 0) dy = -dy;
            int manhattan = dx + dy;
            boolean visible = manhattan <= m.sight &&
                    level.lineOfSight(m.tileX, m.tileY, playerTX, playerTY);
            if (visible) m.noticedUntil = now + 1600;

            // ROM: «иногда тварь пробивает стену» (в картах FAQ такие стены
            // помечены 'B'). У нас это CRACKED-стены, ~5% внутренних стен.
            // Тварь рядом, но НЕ видит игрока -> ломает перегородку и кусает.
            if (m.nextBreach == 0) {
                m.nextBreach = now + 5000 + aiRng.nextInt(7000);
            }
            if (m.windupUntil == 0 && m.fleeUntil <= now && !m.moving &&
                    now >= m.nextBreach && manhattan <= 4 && !visible &&
                    tryBreachWall(m, now)) {
                continue;
            }

            // Замах: тварь замирает, и удар можно переждать/отойти.
            if (m.windupUntil != 0) {
                if (now >= m.windupUntil) {
                    m.windupUntil = 0;
                    int ddx = m.tileX - playerTX; if (ddx < 0) ddx = -ddx;
                    int ddy = m.tileY - playerTY; if (ddy < 0) ddy = -ddy;
                    if (ddx + ddy <= 1) damage(1, now);
                }
                continue;
            }

            // После выстрела тварь «отпрыгивает» от прицела (как в оригинале).
            if (m.fleeUntil > now) {
                if (!m.moving && now >= m.nextThink) {
                    m.nextThink = now + 430;
                    int bestD = manhattan, bx = m.tileX, by = m.tileY;
                    for (int k = 0; k < 4; k++) {
                        int nx = m.tileX + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                        int ny = m.tileY + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                        if (level.isWall(nx, ny) || monsterOccupies(nx, ny, m)) continue;
                        int d = (nx > playerTX ? nx - playerTX : playerTX - nx) +
                                (ny > playerTY ? ny - playerTY : playerTY - ny);
                        if (d > bestD) { bestD = d; bx = nx; by = ny; }
                    }
                    if (bx != m.tileX || by != m.tileY) {
                        m.toX = bx; m.toY = by; m.moveT = 0; m.moving = true;
                    }
                }
                continue;
            }

            // Укус только вплотную, с паузой между атаками.
            if (manhattan <= 1 && visible && now >= m.nextAttack) {
                m.windupUntil = now + 800;
                m.nextAttack = now + m.attackCooldown;
                m.frameIndex = 1;
                continue;
            }

            if (!m.moving && now >= m.nextThink) {
                m.nextThink = now + m.speedMs + aiRng.nextInt(500);
                if (visible || m.noticedUntil > now) {
                    int curDist = level.dist[level.idx(m.tileX, m.tileY)];
                    int bestD = curDist, bx = m.tileX, by = m.tileY;
                    for (int k = 0; k < 4; k++) {
                        int nx = m.tileX + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                        int ny = m.tileY + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                        if (!level.inside(nx, ny)) continue;
                        // редко проламывает трещину, только когда игрок совсем рядом
                        if (level.isBreakable(nx, ny) && manhattan <= 2 && aiRng.chance(3)) {
                            if (level.breakWall(nx, ny)) {
                                level.computeDist(playerTX, playerTY);
                                nextDistCompute = now + 600;
                            }
                        }
                        if (nx == playerTX && ny == playerTY) continue; // на игрока не наступаем
                        if (level.isWall(nx, ny) || monsterOccupies(nx, ny, m)) continue;
                        int nd = level.dist[level.idx(nx, ny)];
                        if (nd >= 0 && nd < bestD) { bestD = nd; bx = nx; by = ny; }
                    }
                    if (bx != m.tileX || by != m.tileY) {
                        m.toX = bx; m.toY = by; m.moveT = 0; m.moving = true;
                    }
                } else if (now >= m.nextWander && aiRng.chance(28)) {
                    // Медленное блуждание в радиусе 3 клеток от «дома».
                    // В ROM твари ещё и «стряфят» — ходят боком относительно игрока.
                    m.nextWander = now + 2200 + aiRng.nextInt(1600);
                    int k = aiRng.nextInt(4);
                    if (aiRng.chance(35)) {
                        int ax = playerTX > m.tileX ? 1 : (playerTX < m.tileX ? -1 : 0);
                        int ay = playerTY > m.tileY ? 1 : (playerTY < m.tileY ? -1 : 0);
                        if (ax != 0) k = aiRng.chance(50) ? 2 : 3;
                        else if (ay != 0) k = aiRng.chance(50) ? 0 : 1;
                    }
                    int nx = m.tileX + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                    int ny = m.tileY + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                    int hdx = nx - m.homeX; if (hdx < 0) hdx = -hdx;
                    int hdy = ny - m.homeY; if (hdy < 0) hdy = -hdy;
                    if (hdx + hdy <= 3 && !level.isWall(nx, ny) &&
                            !monsterOccupies(nx, ny, m)) {
                        m.toX = nx; m.toY = ny; m.moveT = 0; m.moving = true;
                    }
                }
            }
        }
    }

    /**
     * Пробой стены по образцу ROM: ищем CRACKED-стену, за которой сразу
     * коридор в сторону игрока, ломаем её, тварь шагает в пролом и замирает
     * в позе выпада, чтобы игрок успел среагировать. Урон всё равно 1 сердце.
     */
    private boolean tryBreachWall(Monster m, long now) {
        int[] dxs = {1, -1, 0, 0};
        int[] dys = {0, 0, 1, -1};
        for (int k = 0; k < 4; k++) {
            int wx = m.tileX + dxs[k], wy = m.tileY + dys[k];
            if (!level.isBreakable(wx, wy)) continue;
            int bx = wx + dxs[k], by = wy + dys[k];
            if (level.isWall(bx, by)) continue;
            int d = (bx > playerTX ? bx - playerTX : playerTX - bx) +
                    (by > playerTY ? by - playerTY : playerTY - by);
            if (d > 2) continue;
            int cur = (m.tileX > playerTX ? m.tileX - playerTX : playerTX - m.tileX) +
                    (m.tileY > playerTY ? m.tileY - playerTY : playerTY - m.tileY);
            if (d >= cur) continue;
            if (!level.breakWall(wx, wy)) continue;

            level.computeDist(playerTX, playerTY);
            nextDistCompute = now + 600;
            level.markSeen(bx, by);
            // Пролом виден игроку: открываем соседние клетки.
            for (int yy = by - 1; yy <= by + 1; yy++) {
                for (int xx = bx - 1; xx <= bx + 1; xx++) level.markSeen(xx, yy);
            }
            // Пролом — один резкий рывок: тварь уже в образовавшейся клетке.
            m.tileX = m.toX = wx;
            m.tileY = m.toY = wy;
            m.x = wx * FP + FP / 2;
            m.y = wy * FP + FP / 2;
            m.moveT = FP;
            m.moving = false;
            m.windupUntil = now + 900;
            m.nextAttack = now + m.attackCooldown;
            m.noticedUntil = now + 2500;
            m.frameIndex = 1;
            m.nextBreach = now + 16000 + aiRng.nextInt(9000);
            play(Audio.BREACH);
            setMessage("ТВАРЬ ПРОБИЛА СТЕНУ!", C_RED);
            return true;
        }
        return false;
    }

    private boolean monsterOccupies(int tx, int ty, Monster self) {
        for (int i = 0; i < level.monsterCount; i++) {
            Monster o = level.monsters[i];
            if (o == self || !o.alive) continue;
            if (o.tileX == tx && o.tileY == ty) return true;
            if (o.moving && o.toX == tx && o.toY == ty) return true;
        }
        return false;
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
            if (hasKey) finishLevel(now);
            else setMessage("ГЕРМОДВЕРЬ: НУЖЕН ПРОПУСК", C_YELLOW);
            return;
        }
        if (t != Level.FLOOR) {
            setMessage("БЕТОН. НЕ ПРОЙТИ", C_GREY);
            return;
        }
        if (monsterOccupies(tx, ty, null)) {
            // В оригинале тварь просто ЗАКРЫВАЕТ коридор — урона от толчка нет.
            setMessage("ТВАРЬ ЗАКРЫЛА КОРИДОР", C_RED);
            return;
        }
        playerTX = tx;
        playerTY = ty;
        posX = tx * FP + FP / 2;
        posY = ty * FP + FP / 2;
        play(Audio.STEP);
        for (int yy = ty - 1; yy <= ty + 1; yy++) {
            for (int xx = tx - 1; xx <= tx + 1; xx++) level.markSeen(xx, yy);
        }
        int item = level.itemAt(tx, ty);
        if (item >= 0) pickup(level.itemType[item], item, now);
        level.computeDist(playerTX, playerTY);
    }

    private void pickup(int type, int item, long now) {
        play(Audio.PICKUP);
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
            default:
                timeLeftMs += 20000;
                setMessage("ТАЛОН: +20 СЕКУНД", C_GREEN);
                break;
        }
        hudDirty = true;
        level.removeItem(item);
        autosave();
    }

    private void fire(long now) {
        if (showMap || !aiming) return;
        if (magazine <= 0) {
            setMessage("МАГАЗИН ПУСТ: КЛАВИША 0", C_YELLOW);
            return;
        }
        magazine--;
        hudDirty = true;
        flashUntil = now + 55;
        muzzleUntil = now + 120;
        lastShotAt = now;
        play(Audio.SHOT);
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
        // ROM: на тварь уходит ровно 2–8 пуль, выстрел снимает 1 HP.
        m.hp -= 1;
        if (m.hp <= 0) {
            m.alive = false;
            m.respawnAt = now + 20000;
            play(Audio.KILL);
            setMessage("ТВАРЬ РАЗОБРАНА НА ЧАСТИ", C_GREEN);
            buzz(80);
        } else {
            // «После выстрела тварь внезапно уходит в сторону от прицела».
            m.fleeUntil = now + 2600;
            m.windupUntil = 0;
            m.nextThink = now;
            play(Audio.HIT);
            setMessage("ПОПАДАНИЕ: " + m.name, C_YELLOW);
        }
        autosave();
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
        play(Audio.RELOAD);
        setMessage("ПЕРЕЗАРЯДКА +" + take, C_YELLOW);
    }

    private void damage(int dmg, long now) {
        if (now - lastDamage < DAMAGE_COOLDOWN) return;
        lastDamage = now;
        health -= dmg;
        hudDirty = true;
        flashUntil = now + 180;
        play(Audio.HURT);
        if (health <= 0) {
            deathReason = "ТВАРИ ДОСТАЛИ ЛИКВИДАТОРА";
            die(now);
        } else {
            setMessage("УКУС! СЕРДЕЦ: " + health, C_RED);
            buzz(120);
        }
    }

    private void die(long now) {
        deaths++;
        ending = Endings.DEAD;
        // Откат к началу этажа — автосейв не даёт попасть в смертельную петлю.
        Save.copyFloorToAuto();
        hasSave = Save.hasAuto();
        state = DEAD;
        endMenuIndex = 0;
        stateSince = now;
        music(-1);
        buzz(250);
    }

    private void finishLevel(long now) {
        play(Audio.DOOR);
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
            ending = Endings.choose(deaths, totalTimeLeft, magazine + reserve,
                    fastFloors, minFloor);
            Save.clear();
            hasSave = false;
            state = ENDING;
            endMenuIndex = 0;
            stateSince = now;
            music(-1);
            buzz(200);
        } else {
            buildLevel();
            state = BRIEF;
            stateSince = now;
        }
    }

    private void newGame() {
        Save.clear();
        runSeed = 0x1a2b000 | (int) (System.currentTimeMillis() & 1023);
        stage = 0;
        floorNumber = 556;
        deaths = 0;
        fastFloors = 0;
        totalTimeLeft = 0;
        minFloor = 556;
        deathReason = "";
        resetPlayer();
        buildLevel();
        state = BRIEF;
        stateSince = System.currentTimeMillis();
        helpReturn = TITLE;
        pauseReturn = PLAY;
        aiRng = new Rng(runSeed ^ 0x5bf03635L);
        hasSave = true;
        music(0);
    }

    private boolean continueGame() {
        byte[] data = Save.readAuto();
        if (data == null) return false;
        if (!restore(data)) {
            Save.clear();
            hasSave = false;
            return false;
        }
        state = PLAY;
        stateSince = System.currentTimeMillis();
        music(sirenDone ? 1 : 0);
        hasSave = true;
        return true;
    }

    private void resetPlayer() {
        health = 3;
        magazine = 6;
        reserve = 6;
        hasKey = hasWhite = hasBlack = false;
        showMap = false;
        aiming = false;
        message = "";
        messageUntil = 0;
        aimX = w >> 1;
        aimY = viewH >> 1;
        dirDeg = 0;
        setVectors();
        hudDirty = true;
        lastDamage = 0;
        lastShotAt = 0;
        muzzleUntil = 0;
    }

    private void buildLevel() {
        level = new Level(runSeed, floorNumber, stage);
        playerTX = level.startX;
        playerTY = level.startY;
        posX = playerTX * FP + FP / 2;
        posY = playerTY * FP + FP / 2;
        setVectors();
        level.computeDist(playerTX, playerTY);
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
        nextDistCompute = 0;
        nextRespawnCheck = 0;
        hudDirty = true;
        hudSecond = -1;
        sirenDone = false;
        music(0);
        checkpoint();
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

    private void play(int id) {
        if (audio != null) audio.play(id);
    }

    private void music(int track) {
        if (audio != null) audio.music(track);
    }

    private void buzz(int ms) {
        if (midlet == null) return;
        try {
            Display.getDisplay(midlet).vibrate(ms);
        } catch (Exception e) {
        }
    }

    // ----------------------------------------------------------- автосохранение

    private byte[] snapshot() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(SAVE_MAGIC);
        out.writeInt(SAVE_VERSION);
        out.writeInt(runSeed);
        out.writeInt(stage);
        out.writeInt(floorNumber);
        out.writeInt(deaths);
        out.writeInt(fastFloors);
        out.writeInt(totalTimeLeft);
        out.writeInt(minFloor);
        out.writeInt(health);
        out.writeInt(magazine);
        out.writeInt(reserve);
        out.writeBoolean(hasKey);
        out.writeBoolean(hasWhite);
        out.writeBoolean(hasBlack);
        out.writeBoolean(aiming);
        out.writeBoolean(renderer.vbMode);
        out.writeBoolean(musicOn);
        out.writeLong(timeLeftMs);
        out.writeInt(playerTX);
        out.writeInt(playerTY);
        out.writeInt(dirDeg);
        out.writeInt(aimX);
        out.writeInt(aimY);
        level.write(out);
        out.flush();
        return bos.toByteArray();
    }

    private boolean restore(byte[] data) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != SAVE_MAGIC) return false;
            if (in.readInt() != SAVE_VERSION) return false;
            runSeed = in.readInt();
            stage = in.readInt();
            floorNumber = in.readInt();
            deaths = in.readInt();
            fastFloors = in.readInt();
            totalTimeLeft = in.readInt();
            minFloor = in.readInt();
            health = in.readInt();
            magazine = in.readInt();
            reserve = in.readInt();
            hasKey = in.readBoolean();
            hasWhite = in.readBoolean();
            hasBlack = in.readBoolean();
            aiming = in.readBoolean();
            boolean vb = in.readBoolean();
            musicOn = in.readBoolean();
            timeLeftMs = in.readLong();
            playerTX = in.readInt();
            playerTY = in.readInt();
            dirDeg = in.readInt();
            aimX = in.readInt();
            aimY = in.readInt();
            if (!aiming) { aimX = w >> 1; aimY = viewH >> 1; }
            Level restored = Level.restore(in);
            level = restored;
            posX = playerTX * FP + FP / 2;
            posY = playerTY * FP + FP / 2;
            setVectors();
            level.computeDist(playerTX, playerTY);
            renderer.setVbMode(vb);
            if (audio != null) audio.setEnabled(musicOn);
            showMap = false;
            message = "";
            messageUntil = 0;
            deathReason = "";
            hudDirty = true;
            hudSecond = -1;
            nextDistCompute = 0;
            nextRespawnCheck = 0;
            lastDamage = 0;
            sirenDone = timeLeftMs < SIREN_MS;
            lastSaveAt = System.currentTimeMillis();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Exception e) {}
        }
    }

    private void autosave() {
        if (state == TITLE || state == HELP || state == ENDING || state == DEAD) return;
        try {
            byte[] b = snapshot();
            Save.writeAuto(b);
            lastSaveAt = System.currentTimeMillis();
        } catch (Throwable t) {
        }
    }

    private void checkpoint() {
        try {
            byte[] b = snapshot();
            Save.writeFloor(b);
            Save.writeAuto(b);
            lastSaveAt = System.currentTimeMillis();
        } catch (Throwable t) {
        }
    }

    // ------------------------------------------------------------------ отрисовка

    private void renderFrame(long now) {
        int[] buf = renderer.screen;
        fill(buf, 0xff0d0812);
        switch (state) {
            case TITLE: drawTitle(buf, now); break;
            case HELP: drawHelp(buf); break;
            case BRIEF: drawBrief(buf); break;
            case PLAY:
                renderer.render(level, posX, posY, dirX, dirY, planeX, planeY, now);
                if (aiming) drawWeapon(buf, now);
                drawHud(buf, now);
                if (aiming) drawCrosshair(buf);
                if (showMap) drawMap(buf);
                else drawMessage(buf);
                if (now < flashUntil) drawFlash(buf);
                break;
            case ENDING:
            case DEAD:
                drawEnding(buf, now);
                break;
            case PAUSE:
                renderer.render(level, posX, posY, dirX, dirY, planeX, planeY, now);
                if (aiming) drawWeapon(buf, now);
                drawHud(buf, now);
                drawPause(buf, now);
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

    // --------------------------------------------------------------- оружие 1-е лицо

    private static Raster makeWeapon() {
        // «Ералашников» ликвидатора: крупный пистолет из нижнего правого угла,
        // ствол смотрит вверх-вправо к центру прицела.
        Raster g = new Raster(64, 54);
        int black = 0xff0b0b10;
        int metal = 0xff5c606b;
        int steel = 0xff9fa3ab;
        int bright = 0xffd6d8dc;
        int wood = 0xff8a552c;
        int woodLight = 0xffbd7f3c;
        int glove = 0xff2f4636;
        int gloveLight = 0xff528263;
        int purple = 0xffc65ed4;

        // --- тёмный контур, чтобы оружие читалось на любом фоне ---
        g.line(26, 36, 50, 4, black, 17);   // ствол
        g.rect(17, 26, 34, 19, black);      // затвор
        g.line(25, 39, 15, 53, black, 15); // рукоять
        g.ellipse(19, 48, 16, 10, black);  // перчатка
        g.rect(0, 47, 24, 10, black);      // предплечье

        // --- ствол и дульный срез ---
        g.line(27, 35, 49, 6, metal, 12);
        g.line(29, 33, 48, 8, steel, 4);
        g.line(31, 31, 47, 10, bright, 2);
        g.rect(45, 1, 10, 9, steel);
        g.rect(47, 3, 6, 5, black);
        g.rect(48, 4, 4, 3, 0xff303038);

        // --- затворная рама ---
        g.rect(19, 28, 30, 14, metal);
        g.rect(20, 29, 28, 3, steel);
        g.rect(20, 33, 28, 2, bright);
        g.rect(19, 40, 30, 3, black);
        for (int i = 0; i < 4; i++) g.rect(23 + i * 6, 35, 3, 3, black);

        // --- прицельные приспособления с фиолетовым индикатором Партии ---
        g.rect(20, 24, 8, 4, black);
        g.rect(22, 24, 4, 2, purple);
        g.rect(44, 17, 4, 5, black);
        g.rect(45, 18, 2, 3, purple);

        // --- магазин и спусковая скоба ---
        g.rect(16, 40, 13, 9, metal);
        g.rect(17, 41, 11, 2, steel);
        g.rect(30, 41, 12, 3, black);
        g.rect(40, 40, 3, 6, black);

        // --- рукоять с деревом накладки ---
        g.line(26, 40, 17, 52, wood, 10);
        g.line(27, 41, 19, 50, woodLight, 3);

        // --- перчатка ликвидатора ---
        g.rect(1, 48, 22, 8, glove);
        g.rect(1, 48, 22, 2, gloveLight);
        g.ellipse(20, 47, 13, 8, glove);
        g.ellipse(18, 45, 9, 4, gloveLight);
        for (int i = 0; i < 4; i++) {
            g.line(12 + i * 4, 43, 14 + i * 4, 39, gloveLight, 2);
        }
        return g;
    }

    private void drawWeapon(int[] buf, long now) {
        int scale = 2;
        int bw = weaponSprite.w * scale;
        int bh = weaponSprite.h * scale;
        int x = (w - bw) >> 1;
        int recoil = (now - lastShotAt < 130) ? 9 : 0;
        int y = viewH - bh + 10 + recoil;
        blit(weaponSprite, x, y, scale);
        if (now < muzzleUntil) {
            int mx = x + 49 * scale;
            int my = y + 5 * scale;
            for (int r = 0; r < 5; r++) {
                hline(buf, mx - 12 + r, mx + 12 - r, my - 4 + r, 0xffd8cf40);
                hline(buf, mx - 12 + r, mx + 12 - r, my + 4 - r, 0xffe05050);
            }
            box(buf, mx - 4, my - 4, 8, 8, 0xffffe9a8, 0xffffe9a8);
        }
    }

    // ----------------------------------------------------------------------- меню

    private void drawTitle(int[] buf, long now) {
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
        center("ГИГАХРУЩЁВКА", 8, C_PURPLE, 2);
        center("САМОСБОР: ЛИКВИДАТОР", 30, C_WHITE, 1);
        center("J2ME / MIDI / 3D ОТ ПЕРВОГО ЛИЦА", 42, C_GREY, 1);

        for (int i = 0; i < titleMonsters.length; i++) {
            blit(titleMonsters[i], 16 + i * (w / 3), 56, 1);
        }

        int boxY = 116;
        box(buf, 6, boxY, w - 12, h - boxY - 14, 0xff140b1c, C_PURPLE);
        for (int i = 0; i < MENU.length; i++) {
            int y = boxY + 10 + i * 19;
            if (i == menuIndex) {
                box(buf, 10, y - 3, w - 20, 17, 0xff3a2048, C_YELLOW);
            }
            String label = MENU[i];
            if (i == 3) label = musicOn ? "МУЗЫКА: ВКЛ" : "МУЗЫКА: ВЫКЛ";
            if (i == 1 && !hasSave) label = "ПРОДОЛЖИТЬ - НЕТ СЕЙВА";
            int c = i == menuIndex ? C_YELLOW :
                    ((i == 1 && !hasSave) ? C_GREY : C_WHITE);
            BitmapFont.drawShadow(buf, w, h, (i == menuIndex ? ">" : " "), 14, y, c, 1);
            BitmapFont.drawShadow(buf, w, h, label, 26, y, c, 1);
        }
        int fy = h - 28;
        BitmapFont.drawShadow(buf, w, h, "2/8 ВЫБОР  5 OK  4/6 ЗНАЧЕНИЕ", 12, fy, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h,
                hasSave ? "АВТОСЕЙВ: ЕСТЬ  * VB" : "АВТОСЕЙВ: НЕТ  * VB",
                12, fy + 11, hasSave ? C_GREEN : C_GREY, 1);
        if (message.length() > 0) {
            BitmapFont.drawShadow(buf, w, h, message, 12, boxY - 12, messageColor, 1);
        }
    }

    private void drawHelp(int[] buf) {
        fill(buf, 0xff120a1a);
        center("УПРАВЛЕНИЕ", 8, C_PURPLE, 2);
        int y = 34;
        String[] lines = {
            "ЛЕВЫЙ РЕЖИМ (ХОДЬБА)",
            "2 ВПЕРЁД  8 НАЗАД",
            "4 ПОВОРОТ 90  6 ПОВОРОТ 90",
            "",
            "7 ДОСТАТЬ ОРУЖИЕ",
            "В РЕЖИМЕ ПРИЦЕЛА:",
            "2/4/6/8 ДВИГАЮТ ПРИЦЕЛ",
            "5 ОГОНЬ  0 ПЕРЕЗАРЯДКА",
            "7 УБРАТЬ ОРУЖИЕ",
            "",
            "* ОЧКИ VIRTUAL BOY",
            "# КАРТА ЭТАЖА (ТАЙМЕР СТОИТ)",
            "ПРАВ. СОФТ - МЕНЮ ПАУЗЫ",
            "",
            "ЦЕЛЬ: ПРОПУСК + ГЕРМОДВЕРЬ",
            "ДО НАЧАЛА САМОСБОРА",
            "АВТОСОХРАНЕНИЕ КАЖДЫЕ 12 С",
            "",
            "5 ИЛИ 9 - НАЗАД"
        };
        for (int i = 0; i < lines.length; i++) {
            int c = (i == 0 || i == 4 || i == 5) ? C_YELLOW : C_WHITE;
            BitmapFont.drawShadow(buf, w, h, lines[i], 6, y + i * 11, c, 1);
        }
    }

    private void drawBrief(int[] buf) {
        fill(buf, 0xff120a1a);
        for (int y = 0; y < h; y += 8) hline(buf, 0, w - 1, y, 0xff1c1026);
        center("ЭТАЖ " + floorNumber, 16, C_PURPLE, 2);
        center("ЭТАП " + (stage + 1) + " ИЗ 13", 44, C_WHITE, 1);
        int sec = level.timeLimitSec;
        center("ДО САМОСБОРА: " + two(sec / 60) + ":" + two(sec % 60), 62, C_YELLOW, 1);
        box(buf, 8, 82, w - 16, 92, 0xff0d0812, C_PURPLE);
        int y = 90;
        BitmapFont.drawShadow(buf, w, h, "ЗАДАЧА: НАЙТИ ПРОПУСК", 14, y, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "И ОТКРЫТЬ ГЕРМОДВЕРЬ", 14, y + 12, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h, "БЕЛЫЙ ШАР - КАРТА", 14, y + 28, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, "ЧЁРНЫЙ ШАР - ПРЕДМЕТЫ", 14, y + 40, C_GREY, 1);
        BitmapFont.drawShadow(buf, w, h, "7 ОРУЖИЕ  # КАРТА  * VB", 14, y + 56, C_PURPLE, 1);
        BitmapFont.drawShadow(buf, w, h, "ТВАРИ МЕДЛЕННЫ, НО БЛОКИРУЮТ", 14, y + 70, C_GREY, 1);
        center("АВТОСОХРАНЕНИЕ: ВКЛЮЧЕНО", 182, C_GREEN, 1);
        center("5  НАЧАТЬ ЗАБЕГ", 206, C_YELLOW, 1);
    }

    private void drawEnding(int[] buf, long now) {
        fill(buf, 0xff0d0812);
        for (int i = 0; i < 3; i++) {
            int t = (int) (now / 700 + i);
            if ((t & 1) == 0) blit(titleMonsters[i], 8 + i * (w / 3), h - 104, 1);
        }
        String[] lines = Endings.lines(ending);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() == 0) continue;
            int c = i == 0 ? C_PURPLE : (i == 1 ? C_YELLOW : C_WHITE);
            center(lines[i], 8 + i * 12, c, 1);
        }
        if (state == DEAD && deathReason.length() > 0) {
            center(deathReason, 112, C_RED, 1);
        }
        box(buf, 6, 128, w - 12, 40, 0xff140b1c, C_PURPLE);
        BitmapFont.drawShadow(buf, w, h,
                "ЭТАП " + (stage + 1) + "/13  ЭТАЖ " + floorNumber +
                "  БЫСТРЫХ: " + fastFloors, 12, 136, C_WHITE, 1);
        BitmapFont.drawShadow(buf, w, h,
                state == DEAD ? "АВТОСЕЙВ: НАЧАЛО ЭТАЖА" : "АВТОСЕЙВ: ЗАБЕГ ЗАВЕРШЁН",
                12, 150, C_GREEN, 1);

        int n = endMenuCount();
        int my = 180;
        box(buf, 6, my - 6, w - 12, n * 19 + 14, 0xff140b1c, C_PURPLE);
        for (int i = 0; i < n; i++) {
            int y = my + i * 19;
            String label;
            if (state == DEAD) {
                label = i == 0 ? "НОВЫЙ ЗАБЕГ" :
                        (i == 1 ? "ПРОДОЛЖИТЬ С ЭТАЖА" : "В ГЛАВНОЕ МЕНЮ");
            } else {
                label = i == 0 ? "НОВЫЙ ЗАБЕГ" : "В ГЛАВНОЕ МЕНЮ";
            }
            int c = i == endMenuIndex ? C_YELLOW : C_WHITE;
            if (i == endMenuIndex) box(buf, 10, y - 3, w - 20, 17, 0xff3a2048, C_YELLOW);
            BitmapFont.drawShadow(buf, w, h, i == endMenuIndex ? ">" : " ", 14, y, c, 1);
            BitmapFont.drawShadow(buf, w, h, label, 26, y, c, 1);
        }
        center("2/8 ВЫБОР   5 OK", h - 16, C_GREY, 1);
    }

    // --------------------------------------------------------------------- пауза

    private void drawPause(int[] buf, long now) {
        // Затемняем мир, но оставляем его видимым за меню.
        int n = w * viewH;
        if (n > buf.length) n = buf.length;
        for (int i = 0; i < n; i++) {
            int c = buf[i];
            buf[i] = 0xff000000 | ((c & 0xfefefe) >> 1);
        }
        int boxY = 78;
        box(buf, 6, boxY, w - 12, 168, 0xff140b1c, C_PURPLE);
        center("ПАУЗА", boxY + 6, C_PURPLE, 2);
        int sec = (int) (timeLeftMs / 1000);
        if (sec < 0) sec = 0;
        center("САМОСБОР " + two(sec / 60) + ":" + two(sec % 60) +
                "   СЕРДЕЦ " + health + "   ПАТР " + magazine + "/" + reserve,
                boxY + 26, C_WHITE, 1);
        for (int i = 0; i < PAUSE_MENU.length; i++) {
            int y = boxY + 42 + i * 19;
            if (i == pauseMenuIndex) {
                box(buf, 10, y - 3, w - 20, 17, 0xff3a2048, C_YELLOW);
            }
            String label = PAUSE_MENU[i];
            if (i == 2) label = musicOn ? "МУЗЫКА: ВКЛ" : "МУЗЫКА: ВЫКЛ";
            int c = i == pauseMenuIndex ? C_YELLOW : C_WHITE;
            BitmapFont.drawShadow(buf, w, h, i == pauseMenuIndex ? ">" : " ",
                    14, y, c, 1);
            BitmapFont.drawShadow(buf, w, h, label, 26, y, c, 1);
        }
        String note = (pauseNoteUntil > now && pauseNote.length() > 0)
                ? pauseNote : "ТАЙМЕР И ТВАРИ ОСТАНОВЛЕНЫ";
        center(note, boxY + 136, C_GREEN, 1);
        center("2/8 ВЫБОР  5 OK  ПРАВ.СОФТ = НАЗАД", boxY + 150, C_GREY, 1);
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
        hudLine3 = aiming ? "7 УБРАТЬ  2/4/6/8 ПРИЦЕЛ" : "7 ОРУЖИЕ  2/8 ШАГ 4/6 ПОВОРОТ";
        hudLine4 = "5 ОГОНЬ 0 ЗАРЯД * VB # КАРТА";
        StringBuffer s5 = new StringBuffer();
        s5.append("ЭТАП "); s5.append(stage + 1); s5.append("/13 АВТОСЕЙВ ПРАВ=ПАУЗА");
        if (hasWhite) s5.append(" W");
        if (hasBlack) s5.append(" B");
        if (aiming) s5.append(" !");
        hudLine5 = s5.toString();
    }

    private void drawHud(int[] buf, long now) {
        box(buf, 0, viewH, w, hudH, C_DARK, C_PURPLE);
        rebuildHud();
        int y = viewH + 4;
        int col = timeLeftMs < SIREN_MS ? C_RED : C_WHITE;
        BitmapFont.drawShadow(buf, w, h, hudLine1, 4, y, col, 1);
        for (int i = 0; i < 5; i++) {
            BitmapFont.drawShadow(buf, w, h, "^", 4 + i * 8, y + 11,
                    i < health ? C_RED : 0xff3a2048, 1);
        }
        BitmapFont.drawShadow(buf, w, h, hudLine2, 46, y + 11, C_YELLOW, 1);
        BitmapFont.drawShadow(buf, w, h, hudLine3, 4, y + 22, aiming ? C_YELLOW : C_GREY, 1);
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

    private void drawFlash(int[] buf) {
        // Короткая вспышка выстрела / укуса: только яркая рамка, без затирания кадра.
        int c = 0xffa03038;
        for (int t = 0; t < 3; t++) {
            hline(buf, t, w - 1 - t, t, c);
            hline(buf, t, w - 1 - t, viewH - 1 - t, c);
            vline(buf, t, t, viewH - 1 - t, c);
            vline(buf, w - 1 - t, t, viewH - 1 - t, c);
        }
        hline(buf, 0, 14, 0, 0xffe05050);
        hline(buf, w - 15, w - 1, 0, 0xffe05050);
        hline(buf, 0, 14, viewH - 1, 0xffe05050);
        hline(buf, w - 15, w - 1, viewH - 1, 0xffe05050);
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
        if (hasBlack) {
            box(buf, ox + level.keyX * cs, oy + level.keyY * cs, cs, cs, C_YELLOW, C_YELLOW);
            box(buf, ox + level.exitX * cs, oy + level.exitY * cs, cs, cs, C_RED, C_RED);
        }
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
        center("# ВЕРНУТЬСЯ   ЭТАП " + (stage + 1) + "/13", h - 16, C_GREY, 1);
    }
}
