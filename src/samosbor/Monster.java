package samosbor;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * ПРОЦЕДУРНЫЙ МОНСТР ИЗ РАЗНЫХ ЧАСТЕЙ ТЕЛА.
 * Как в Insmouse no Yakata, твари не заранее нарисованы: каждая особь
 * собирается ГПСЧ из головы, торса, рук, ног и «лишней» детали.
 * Из частей же считаются HP, скорость, урон и имя.
 *
 * ВАЖНО: части теперь существуют как ОТДЕЛЬНЫЕ графические элементы,
 * а не как один заранее склеенный спрайт. Их хранением, независимой
 * анимацией когтей и разлётом при смерти занимается MonsterRig.
 */
public final class Monster {

    public static final String[] HEAD = {
        "ГОЛОВА", "ПРОТИВОГАЗ", "ГНИЛУШКА", "ГЛАЗ", "ТЕЛЕВИЗОР", "БЕЗ ГОЛОВЫ"
    };
    public static final String[] TORSO = {
        "ТОРС", "БОЧКА", "ПАНЕЛЬ", "КЛЕТЬ", "СРОСТОК", "КОСТЮМ"
    };
    public static final String[] ARMS = {
        "РУКИ", "ТЕНТАКЛИ", "ГРАБЛИ", "ТРУБЫ", "КЛЕШНИ", "БЕЗРУКИЙ"
    };
    public static final String[] LEGS = {
        "НОГИ", "ЛАПЫ", "КОРНИ", "КОЛЁСА", "ОДНОНОГ", "ПАРИТ"
    };
    public static final String[] EXTRA = {
        "ОБЫЧНЫЙ", "СЛИЗЬ", "МНОГОГЛАЗ", "СПИННЫЕ ЩУПАЛЬЦА", "РЖАВЧИНА", "ФИОЛЕТОВЫЙ ОРЕОЛ"
    };
    private static final String[] HEAD_SHORT = {
        "ГОЛОВ", "МАСК", "ГНИЛ", "ГЛАЗ", "ТВ", "БЕЗГОЛОВ"
    };
    private static final String[] TORSO_SHORT = {
        "ТОРС", "БОЧК", "ПАНЕЛ", "КЛЕТ", "СРОСТ", "КОСТЮМ"
    };
    private static final String[] ARMS_SHORT = {
        "РУК", "ТЕНТАКЛ", "ГРАБЛ", "ТРУБ", "КЛЕШН", "БЕЗРУК"
    };
    private static final String[] LEGS_SHORT = {
        "НОГ", "ЛАП", "КОРН", "КОЛЁС", "ОДНОНОГ", "ПАР"
    };

    // Цветовые схемы: серо-зелёный, самосборный, ржавый, «нелюдь».
    private static final int[] SKIN = {
        0xff788c6e, 0xff8b47a0, 0xff96603c, 0xffbeb9a5
    };

    // Фотореализм спрайтов: макро-снимки кожи (skin0..3) дают поры, вены и
    // слизь, а изолированное фото твари (beast0..3, четыре цветовые морфы)
    // задаёт настоящую фотографическую анатомию поверх ГПСЧ-силуэта.
    private static Texture[] skinTex, beastTex;
    private static boolean photoReady;

    private static void ensurePhotos() {
        if (photoReady) return;
        photoReady = true;
        skinTex = new Texture[4];
        beastTex = new Texture[4];
        for (int i = 0; i < 4; i++) {
            skinTex[i] = Texture.load("/textures/skin" + i + ".png");
            beastTex[i] = Texture.load("/textures/beast" + i + ".png");
        }
    }

    /** Собирает фото-пиксель с лёгким подъёмом яркости для тёмного коридора. */
    private static int photoColor(int c, int shift) {
        int r = ((c >> 16) & 255) * 5 / 4 + shift;
        int g = ((c >> 8) & 255) * 5 / 4 + shift;
        int b = (c & 255) * 5 / 4 + shift;
        return 0xff000000 |
                ((r < 0 ? 0 : r > 255 ? 255 : r) << 16) |
                ((g < 0 ? 0 : g > 255 ? 255 : g) << 8) |
                (b < 0 ? 0 : b > 255 ? 255 : b);
    }

    /**
     * ГПСЧ-спрайт остаётся «скелетом» (голова/руки/ноги/лишняя деталь задают
     * форму и окраску), а поверх него ложится настоящая фотография:
     *  - где фото-тварь светлее фона — берём её пиксели на 78%, сохраняя 22%
     *    процедурного цвета, чтобы части тела всё ещё различались;
     *  - где фото чёрное — оставляем процедурные примитивы, но модулируем их
     *    макро-текстурой кожи (поры, вены, влажные блики);
     *  - свечение глаз не трогаем: тварь читается и в Virtual Boy.
     */
    private static void applyPhotoSkin(Raster g, int palette) {
        applyPhotoSkin(g, palette, true);
    }

    /** Только подмешивает фото в УЖЕ непрозрачные пиксели отдельной части. */
    static void applyPhotoTint(Raster g, int palette) {
        applyPhotoSkin(g, palette, false);
    }

    private static void applyPhotoSkin(Raster g, int palette, boolean expand) {
        ensurePhotos();
        int p = palette < 0 || palette > 3 ? 0 : palette;
        Texture s = skinTex[p], b = beastTex[p];
        if (s.w < 1 || s.h < 1 || b.w < 1 || b.h < 1) return;
        for (int i = 0; i < g.p.length; i++) {
            int c = g.p[i];
            int x = i % g.w, y = i / g.w;
            int bc = b.rgb[(y % b.h) * b.w + (x % b.w)];
            int br = (bc >> 16) & 255, bg = (bc >> 8) & 255, bb = bc & 255;
            int blum = (br * 77 + bg * 150 + bb * 29) >> 8;
            boolean body = blum > 26;
            if ((c >>> 24) == 0) {
                // Фотография может чуть расширить силуэт (щупальца, пальцы),
                // но для ОТДЕЛЬНОЙ части этого делать нельзя: иначе каждый
                // модуль содержал бы копию целой твари.
                if (expand) g.p[i] = body ? photoColor(bc, 10) : 0;
                continue;
            }
            int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            if (cr > 205 && cg > 195 && cb > 190) continue;  // глаза/свечение
            int sc = s.rgb[(y % s.h) * s.w + (x % s.w)];
            int sr = (sc >> 16) & 255, sg = (sc >> 8) & 255, sb = sc & 255;
            int slum = (sr * 77 + sg * 150 + sb * 29) >> 8;
            int r, gg, b2;
            if (body) {
                int pc = photoColor(bc, 8);
                int pr = (pc >> 16) & 255, pg = (pc >> 8) & 255, pb = pc & 255;
                r = (pr * 4 + cr) / 5 + (sr - slum) / 7;
                gg = (pg * 4 + cg) / 5 + (sg - slum) / 7;
                b2 = (pb * 4 + cb) / 5 + (sb - slum) / 7;
            } else {
                int f = 256 + (slum - 128) * 4 / 5;        // 154..358
                r = (cr * f >> 8) + (sr - slum) * 3 / 10;
                gg = (cg * f >> 8) + (sg - slum) * 3 / 10;
                b2 = (cb * f >> 8) + (sb - slum) * 3 / 10;
                if (slum > 190) { r += 10; gg += 12; b2 += 14; }
            }
            g.p[i] = 0xff000000 |
                    ((r < 0 ? 0 : r > 255 ? 255 : r) << 16) |
                    ((gg < 0 ? 0 : gg > 255 ? 255 : gg) << 8) |
                    (b2 < 0 ? 0 : b2 > 255 ? 255 : b2);
        }
    }

    public int head, torso, arms, legs, extra, palette;
    public int hp, maxHp, damage, speedMs, sight;
    public String name;

    public int tileX, tileY;       // текущая клетка
    public int toX, toY;           // клетка, в которую ползёт
    public int x, y;               // позиция в fixed point (tile = 4096)
    public int moveT;              // 0..4096, прогресс перехода
    public boolean moving, alive;
    public long nextThink, nextAttack, respawnAt;
    public int frameIndex;
    /** Девять отдельных графических элементов вместо цельного спрайта. */
    public MonsterRig rig;
    /** Последовательность разрушения: фрагменты ещё видны, но тварь мертва. */
    public boolean dying;
    public long deathStart, deathUntil;

    // Поведение Insmouse no Yakata: тварь не «бежит по BFS», а медленно
    // бродит возле своего места, изредка замечает игрока, перед атакой
    // замирает в позе выпада, а после попадания шарахается в сторону.
    public int homeX, homeY;
    public long windupUntil, fleeUntil, noticedUntil, nextWander;
    public long nextBreach;   // кулдаун «пробить стену» (ROM: иногда ломают стену)
    public int attackCooldown;

    private Monster() {}

    /** Создать случайную особь. */
    public static Monster create(Rng r, int floorIndex) {
        Monster m = new Monster();
        m.head = r.nextInt(HEAD.length);
        m.torso = r.nextInt(TORSO.length);
        m.arms = r.nextInt(ARMS.length);
        m.legs = r.nextInt(LEGS.length);
        m.extra = r.nextInt(EXTRA.length);
        m.palette = r.nextInt(SKIN.length);

        // В оригинале на тварь уходит ровно 2–8 пуль, и у каждой особи
        // это число постоянно (не зависит от того, куда попал выстрел).
        m.maxHp = 2 + (m.torso + m.head / 2 + m.extra / 2 + floorIndex / 6) % 7;
        m.hp = m.maxHp;
        // В оригинале укус снимает ОДНО сердце; твари опасны числом, а не уроном.
        m.damage = 1;
        // Медленные, предсказуемые шаги (в оригинале тварей обходят, а не «кайтят»).
        m.speedMs = 1700 + m.legs * 170 - floorIndex * 12;
        if (m.legs == 5) m.speedMs -= 220;
        if (m.speedMs < 1150) m.speedMs = 1150;
        if (m.speedMs > 2700) m.speedMs = 2700;
        // Чутьё короткое: тварь не преследует через весь этаж.
        m.sight = 3 + m.extra / 2;
        // Длинная пауза между укусами: три сердца не исчезают за пару секунд.
        m.attackCooldown = 6600 - m.arms * 150;
        if (m.attackCooldown < 5600) m.attackCooldown = 5600;
        m.alive = true;
        m.name = makeName(m);

        m.rig = MonsterRig.build(m);
        return m;
    }

    private static String makeName(Monster m) {
        StringBuffer sb = new StringBuffer();
        sb.append(HEAD_SHORT[m.head]);
        sb.append(ARMS_SHORT[m.arms]);
        sb.append("-");
        sb.append(TORSO_SHORT[m.torso]);
        sb.append(LEGS_SHORT[m.legs]);
        return sb.toString();
    }

    /** Восстановление из автосейва: спрайты собираются заново из тех же частей. */
    public static Monster restore(DataInputStream in) throws Exception {
        Monster m = new Monster();
        m.head = in.readByte();
        m.torso = in.readByte();
        m.arms = in.readByte();
        m.legs = in.readByte();
        m.extra = in.readByte();
        m.palette = in.readByte();
        m.maxHp = in.readInt();
        m.hp = in.readInt();
        m.damage = in.readInt();
        m.speedMs = in.readInt();
        m.sight = in.readInt();
        m.attackCooldown = in.readInt();
        m.tileX = in.readInt();
        m.tileY = in.readInt();
        m.toX = in.readInt();
        m.toY = in.readInt();
        m.x = in.readInt();
        m.y = in.readInt();
        m.moveT = in.readInt();
        m.moving = in.readBoolean();
        m.alive = in.readBoolean();
        m.nextThink = in.readLong();
        m.nextAttack = in.readLong();
        m.respawnAt = in.readLong();
        m.windupUntil = in.readLong();
        m.fleeUntil = in.readLong();
        m.noticedUntil = in.readLong();
        m.nextWander = in.readLong();
        m.nextBreach = in.readLong();
        m.homeX = in.readInt();
        m.homeY = in.readInt();
        m.frameIndex = in.readInt();
        m.dying = in.readBoolean();
        m.deathStart = in.readLong();
        m.deathUntil = in.readLong();
        int deathSeed = in.readInt();
        m.name = makeName(m);
        m.rig = MonsterRig.build(m);
        if (m.dying) {
            // Разлёт продолжается после загрузки сейва со своих позиций.
            m.alive = true;                 // фрагменты ещё видимы рендереру
            m.hp = 0;
            m.moving = false;
            m.windupUntil = 0;
            m.fleeUntil = 0;
            m.rig.startDeath(deathSeed);
            if (m.respawnAt == 0) m.respawnAt = m.deathUntil + 20000;
        }
        return m;
    }

    public void write(DataOutputStream out) throws Exception {
        out.writeByte(head);
        out.writeByte(torso);
        out.writeByte(arms);
        out.writeByte(legs);
        out.writeByte(extra);
        out.writeByte(palette);
        out.writeInt(maxHp);
        out.writeInt(hp);
        out.writeInt(damage);
        out.writeInt(speedMs);
        out.writeInt(sight);
        out.writeInt(attackCooldown);
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeInt(toX);
        out.writeInt(toY);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(moveT);
        out.writeBoolean(moving);
        out.writeBoolean(alive);
        out.writeLong(nextThink);
        out.writeLong(nextAttack);
        out.writeLong(respawnAt);
        out.writeLong(windupUntil);
        out.writeLong(fleeUntil);
        out.writeLong(noticedUntil);
        out.writeLong(nextWander);
        out.writeLong(nextBreach);
        out.writeInt(homeX);
        out.writeInt(homeY);
        out.writeInt(frameIndex);
        out.writeBoolean(dying);
        out.writeLong(deathStart);
        out.writeLong(deathUntil);
        out.writeInt(deathSeed);
    }

    /** Seed разлёта сохраняется, чтобы после load фрагменты не прыгали. */
    private int deathSeed;

    public String description() {
        return HEAD[head] + " / " + TORSO[torso] + " / " + ARMS[arms] +
               " / " + LEGS[legs] + " / " + EXTRA[extra];
    }

    public void place(int tx, int ty) {
        tileX = toX = tx;
        tileY = toY = ty;
        x = tx * 4096 + 2048;
        y = ty * 4096 + 2048;
        moveT = 0;
        moving = false;
        alive = true;
        hp = maxHp;
        homeX = tx;
        homeY = ty;
        nextThink = 0;
        nextAttack = 0;
        respawnAt = 0;
        windupUntil = 0;
        fleeUntil = 0;
        noticedUntil = 0;
        nextWander = 0;
        nextBreach = 0;
        frameIndex = 0;
        dying = false;
        deathStart = 0;
        deathUntil = 0;
        deathSeed = 0;
        if (rig != null) rig.reset();
    }

    /** Смерть = не исчезновение, а разлёт девяти отдельных фрагментов. */
    public void startDeath(long now, int deathSeed) {
        this.deathSeed = deathSeed;
        hp = 0;
        dying = true;
        moving = false;
        windupUntil = 0;
        fleeUntil = 0;
        deathStart = now;
        deathUntil = now + MonsterRig.DEATH_MS;
        if (rig != null) rig.startDeath(deathSeed);
    }

    public boolean finishDeath(long now) {
        if (!dying || now < deathUntil) return false;
        dying = false;
        alive = false;
        return true;
    }

    /** Цельный кадр только для титула/тестов; в бою рендерер берёт rig. */
    public Raster snapshot() {
        return rig != null ? rig.snapshot() : new Raster(40, 56);
    }

    /** Позиция с интерполяцией между клетками. */
    public void advance(int dtMs) {
        if (!moving) return;
        moveT += (dtMs * 4096) / speedMs;
        int fx = tileX * 4096 + 2048;
        int fy = tileY * 4096 + 2048;
        int txp = toX * 4096 + 2048;
        int typ = toY * 4096 + 2048;
        if (moveT >= 4096) {
            moveT = 4096;
            x = txp;
            y = typ;
            tileX = toX;
            tileY = toY;
            moving = false;
        } else {
            x = fx + (int) (((long) (txp - fx) * moveT) >> 12);
            y = fy + (int) (((long) (typ - fy) * moveT) >> 12);
        }
    }

    /**
     * Рисует ОДНУ отдельную часть тела в её собственном 40x56 буфере.
     * Используется для экспорта открытых PNG (gfx/parts/) и как фолбэк,
     * если атлас удалён из JAR. В игре эти части не склеиваются.
     */
    static Raster renderPart(int kind, int variant, int palette) {
        if (kind == MonsterRig.BODY) return photoBody(palette);
        int p = palette < 0 || palette > 3 ? 0 : palette;
        Raster g = new Raster(MonsterRig.CELL_W, MonsterRig.CELL_H);
        int skin = SKIN[p];
        int dark = Raster.darker(skin, 55);
        int light = Raster.lighter(skin, 35);
        int metal = 0xff626570, steel = 0xff9a9ca0;
        int purple = 0xffb952c7, purpleDark = 0xff611c6d;
        int glow = 0xffe08ae8, black = 0xff101016;
        switch (kind) {
            case MonsterRig.SHADOW:
                g.ellipse(20, 53, 13, 2, 0xff2a1030);
                if (variant == 5) g.ellipse(20, 48, 11, 5, purpleDark);
                return g;                       // тень остаётся тёмной
            case MonsterRig.BACK:
                drawExtraBack(g, variant, purple, purpleDark, light, 0);
                break;
            case MonsterRig.LEGS:
                drawLegs(g, variant, skin, dark, metal, steel, purple, 0, 0);
                break;
            case MonsterRig.ARM_L:
            case MonsterRig.ARM_R: {
                // Конечности рисуются попарно; делим холст пополам, чтобы
                // левая и правая стали ДВУМЯ независимыми модулями.
                Raster tmp = new Raster(MonsterRig.CELL_W, MonsterRig.CELL_H);
                drawArms(tmp, variant, skin, dark, metal, steel, purple, glow, 0, 0);
                int lo = kind == MonsterRig.ARM_L ? 0 : 20;
                int hi = kind == MonsterRig.ARM_L ? 20 : 40;
                for (int y = 0; y < MonsterRig.CELL_H; y++) {
                    for (int x = lo; x < hi; x++) {
                        g.p[y * MonsterRig.CELL_W + x] =
                                tmp.p[y * MonsterRig.CELL_W + x];
                    }
                }
                break;
            }
            case MonsterRig.TORSO:
                drawTorso(g, variant, skin, dark, light, metal, steel, purple, black);
                break;
            case MonsterRig.HEAD:
                drawHead(g, variant, skin, dark, light, metal, steel,
                         purple, glow, black, 0);
                break;
            case MonsterRig.FRONT:
                drawExtraFront(g, variant, purple, glow, light, black, 0);
                break;
            default:
                return null;
        }
        applyPhotoTint(g, p);
        return g;
    }

    /** «Основная часть тела» — фото-силуэт твари из beast*.png. */
    private static Raster photoBody(int palette) {
        ensurePhotos();
        int p = palette < 0 || palette > 3 ? 0 : palette;
        Texture b = beastTex[p];
        Raster g = new Raster(MonsterRig.CELL_W, MonsterRig.CELL_H);
        if (b == null || b.w < 1 || b.h < 1 || b.rgb == null) return g;
        for (int y = 0; y < MonsterRig.CELL_H; y++) {
            for (int x = 0; x < MonsterRig.CELL_W; x++) {
                int c = b.rgb[(y % b.h) * b.w + (x % b.w)];
                int lum = (((c >> 16) & 255) * 77 + ((c >> 8) & 255) * 150 +
                           (c & 255) * 29) >> 8;
                if (lum > 26) g.p[y * MonsterRig.CELL_W + x] = photoColor(c, 10);
            }
        }
        return g;
    }

    private static void drawLegs(Raster g, int kind, int skin, int dark, int metal,
                                 int steel, int purple, int phase, int wob) {
        int black = 0xff101016;
        int lift = phase == 0 ? 0 : 2;
        switch (kind) {
            case 0: // НОГИ
                g.line(16, 38, 15 - lift, 51, dark, 3);
                g.line(24, 38, 25 + lift, 51, skin, 3);
                g.rect(11 - lift, 50, 8, 4, black);
                g.rect(22 + lift, 50, 8, 4, black);
                break;
            case 1: // ЛАПЫ
                g.line(16, 38, 11, 49, skin, 5);
                g.line(24, 38, 29, 49, skin, 5);
                for (int i = 0; i < 3; i++) {
                    g.line(10 + i * 2, 50, 7 + i * 2, 54, steel, 1);
                    g.line(27 + i * 2, 50, 30 + i * 2, 54, steel, 1);
                }
                break;
            case 2: // КОРНИ
                for (int i = 0; i < 4; i++) {
                    int sx = 14 + i * 4;
                    g.line(sx, 38, sx - 5 + i * 2 + lift, 46, dark, 2);
                    g.line(sx - 5 + i * 2 + lift, 46, sx - 8 + i * 3, 54, skin, 2);
                }
                break;
            case 3: // КОЛЁСА
                g.ellipse(13, 48, 6, 6, black);
                g.ellipse(27, 48, 6, 6, black);
                g.ring(13, 48, 6, 6, 40, metal);
                g.ring(27, 48, 6, 6, 40, metal);
                g.ellipse(13, 48, 2, 2, steel);
                g.ellipse(27, 48, 2, 2, steel);
                break;
            case 4: // ОДНА НОГА
                g.rect(16, 38, 8, 15, dark);
                g.rect(17, 38, 6, 15, skin);
                g.rect(12, 51, 16, 3, metal);
                break;
            default: // ПАРИТ
                g.ellipse(20, 44, 9, 4, purple);
                g.ellipse(20, 44, 5, 2, 0xffe08ae8);
                break;
        }
    }

    private static void drawExtraBack(Raster g, int kind, int purple, int purpleDark,
                                      int light, int phase) {
        if (kind == 3) { // спинные щупальца
            for (int i = 0; i < 3; i++) {
                int xx = 8 + i * 12;
                g.line(xx, 26, xx - 4 + phase * 2, 12, purpleDark, 3);
                g.line(xx - 4 + phase * 2, 12, xx - 7, 5, purple, 2);
            }
        } else if (kind == 5) { // ореол
            g.ring(20, 15, 15, 15, 75, purple);
            g.ellipse(20, 1, 2, 2, light);
        }
    }

    private static void drawArms(Raster g, int kind, int skin, int dark, int metal,
                                 int steel, int purple, int glow, int phase, int wob) {
        int purpleDark = 0xff611c6d;
        int ly = 36 + wob;
        int ry = 36 - wob;
        switch (kind) {
            case 0: // РУКИ
                g.line(12, 25, 5, ly, skin, 3);
                g.line(28, 25, 35, ry, skin, 3);
                g.ellipse(4, ly + 1, 3, 3, dark);
                g.ellipse(36, ry + 1, 3, 3, dark);
                break;
            case 1: // ТЕНТАКЛИ
                g.line(12, 25, 2, 30 + wob, purple, 3);
                g.line(2, 30 + wob, 5, 40 - wob, purpleDark, 3);
                g.line(5, 40 - wob, 1, 47, purple, 2);
                g.line(28, 25, 38, 30 - wob, purple, 3);
                g.line(38, 30 - wob, 35, 40 + wob, purpleDark, 3);
                g.line(35, 40 + wob, 39, 47, purple, 2);
                g.ellipse(1, 47, 2, 2, glow);
                g.ellipse(39, 47, 2, 2, glow);
                break;
            case 2: // ГРАБЛИ (оружие ликвидаторов, ставшее частью твари)
                g.line(12, 25, 4, ly, metal, 3);
                g.line(28, 25, 36, ry, metal, 3);
                g.rect(0, ly, 9, 2, steel);
                g.rect(31, ry, 9, 2, steel);
                for (int i = 0; i < 4; i++) {
                    g.line(1 + i * 2, ly + 2, 1 + i * 2, ly + 6, steel, 1);
                    g.line(32 + i * 2, ry + 2, 32 + i * 2, ry + 6, steel, 1);
                }
                break;
            case 3: // ТРУБЫ
                g.line(12, 25, 4, ly, metal, 5);
                g.line(28, 25, 36, ry, metal, 5);
                g.rect(2, ly - 1, 5, 3, steel);
                g.rect(33, ry - 1, 5, 3, steel);
                break;
            case 4: // КЛЕШНИ
                g.line(12, 25, 4, 30 + wob, dark, 4);
                g.line(28, 25, 36, 30 - wob, dark, 4);
                g.line(4, 30 + wob, 1, 38 + wob, skin, 3);
                g.line(4, 30 + wob, 8, 38 + wob, skin, 3);
                g.line(36, 30 - wob, 39, 38 - wob, skin, 3);
                g.line(36, 30 - wob, 32, 38 - wob, skin, 3);
                break;
            default: // БЕЗРУКИЙ
                g.ellipse(11, 26, 2, 2, dark);
                g.ellipse(29, 26, 2, 2, dark);
                break;
        }
    }

    private static void drawTorso(Raster g, int kind, int skin, int dark, int light,
                                  int metal, int steel, int purple, int black) {
        switch (kind) {
            case 0: // ТОРС
                g.rect(12, 21, 16, 19, dark);
                g.rect(13, 22, 14, 17, skin);
                g.line(13, 27, 26, 27, dark, 1);
                g.line(13, 32, 26, 32, dark, 1);
                break;
            case 1: // БОЧКА
                g.ellipse(20, 31, 10, 11, 0xff85532c);
                g.ellipse(20, 31, 8, 10, 0xff6a3d22);
                g.rect(10, 26, 20, 2, metal);
                g.rect(10, 35, 20, 2, metal);
                break;
            case 2: // ПАНЕЛЬ
                g.rect(9, 21, 22, 19, 0xff4a4b50);
                g.rect(9, 21, 22, 1, steel);
                g.line(12, 22, 16, 31, 0xff2b2c33, 1);
                g.line(16, 31, 14, 39, 0xff2b2c33, 1);
                g.rect(9, 30, 22, 1, 0xff2b2c33);
                break;
            case 3: // КЛЕТЬ
                g.rect(11, 21, 18, 19, black);
                g.ellipse(20, 31, 7, 8, purple);
                for (int i = 0; i < 4; i++) g.rect(12 + i * 5, 21, 2, 19, metal);
                g.rect(11, 21, 18, 2, steel);
                g.rect(11, 38, 18, 2, steel);
                break;
            case 4: // СРОСТОК
                g.ellipse(15, 27, 7, 8, skin);
                g.ellipse(25, 29, 8, 7, dark);
                g.ellipse(20, 35, 9, 6, light);
                g.ellipse(13, 36, 5, 4, skin);
                break;
            default: // КОСТЮМ ликвидатора
                g.rect(11, 21, 18, 19, 0xff354a38);
                g.rect(11, 21, 18, 2, metal);
                g.line(20, 23, 20, 40, steel, 1);
                g.rect(11, 34, 18, 2, dark);
                g.ellipse(24, 28, 3, 3, metal);
                break;
        }
    }

    private static void drawHead(Raster g, int kind, int skin, int dark, int light,
                                 int metal, int steel, int purple, int glow,
                                 int black, int phase) {
        switch (kind) {
            case 0: // ГОЛОВА
                g.ellipse(20, 12, 8, 9, dark);
                g.ellipse(20, 12, 7, 8, skin);
                g.ellipse(16, 11, 2, 2, 0xffe8e0c0);
                g.ellipse(24, 11, 2, 2, 0xffe8e0c0);
                g.ellipse(16, 11, 1, 1, black);
                g.ellipse(24, 11, 1, 1, black);
                g.rect(17, 17, 6, 1, black);
                break;
            case 1: // ПРОТИВОГАЗ
                g.ellipse(20, 12, 8, 9, dark);
                g.ellipse(14, 9, 3, 3, steel);
                g.ellipse(26, 9, 3, 3, steel);
                g.ellipse(14, 9, 2, 2, 0xff9ad0c0);
                g.ellipse(26, 9, 2, 2, 0xff9ad0c0);
                g.ellipse(20, 16, 4, 4, metal);
                g.ellipse(20, 16, 2, 2, black);
                g.line(20, 20, 20, 23, metal, 3);
                break;
            case 2: // ГНИЛУШКА
                g.rect(17, 13, 6, 9, light);
                g.ellipse(20, 10, 11, 7, purple);
                g.ellipse(20, 9, 10, 5, 0xff8b2c9b);
                g.ellipse(14, 8, 2, 2, glow);
                g.ellipse(25, 7, 2, 2, glow);
                g.ellipse(21, 11, 2, 2, 0xffe08ae8);
                break;
            case 3: // ГЛАЗ
                g.ellipse(20, 12, 11, 9, 0xffe8e0d0);
                g.ellipse(20, 12, 6, 6, purple);
                g.ellipse(20, 12, 3, 4, black);
                g.ellipse(19, 10, 1, 1, glow);
                g.line(9, 6, 12, 8, skin, 1);
                g.line(31, 6, 28, 8, skin, 1);
                break;
            case 4: // ТЕЛЕВИЗОР
                g.rect(10, 5, 20, 16, metal);
                g.rect(12, 7, 16, 12, black);
                g.rect(14, 9, 12, 8, 0xff4a7a5d);
                g.line(14, 13, 26, 13, 0xff9ad0c0, 1);
                g.line(16, 5, 12, 1, steel, 1);
                g.line(24, 5, 28, 1, steel, 1);
                break;
            default: // БЕЗ ГОЛОВЫ
                g.rect(17, 15, 6, 6, dark);
                g.rect(18, 15, 4, 2, purple);
                g.ellipse(20, 21, 3, 2, dark);
                break;
        }
    }

    private static void drawExtraFront(Raster g, int kind, int purple, int glow,
                                       int light, int black, int phase) {
        if (kind == 1) { // слизь
            for (int i = 0; i < 4; i++) {
                int x = 12 + i * 5;
                g.line(x, 34, x + (phase == 0 ? 0 : 1), 44 + i, purple, 1);
                g.ellipse(x + (phase == 0 ? 0 : 1), 45 + i, 1, 1, glow);
            }
        } else if (kind == 2) { // лишние глаза
            g.ellipse(15, 28, 2, 2, glow);
            g.ellipse(25, 28, 2, 2, glow);
            g.ellipse(20, 36, 2, 2, glow);
            g.ellipse(15, 28, 1, 1, black);
            g.ellipse(25, 28, 1, 1, black);
        } else if (kind == 4) { // ржавчина
            for (int i = 0; i < 7; i++) {
                int x = 11 + (i * 7) % 18;
                int y = 23 + (i * 11) % 16;
                g.set(x, y, 0xff85532c);
            }
        }
    }
}
