package samosbor;

/**
 * МОДУЛЬНЫЙ СПРАЙТ (Modular Sprite), как в Insmouse no Yakata.
 *
 * Тварь НЕ является одним цельным изображением. Она собирается из девяти
 * отдельных графических элементов: фото-тело, тень, задние щупальца, ноги,
 * левая и правая конечности с когтями, торс, голова с пастью, передние
 * детали. Каждый элемент — свой Raster со своей базовой позицией (bx,by)
 * в виртуальном спрайте 40x56 и своим текущим смещением (dx,dy).
 *
 * Рендерер НЕ перерисовывает существо целиком: он накладывает уже готовые
 * части, подставляя разные смещения. Поэтому когти двигаются независимо от
 * тела, а при смерти те же девять фрагментов разлетаются по своим
 * траекториям — последовательность разрушения, а не «исчезновение спрайта».
 *
 * Сами PNG частей лежат открыто в JAR: gfx/parts/{kind}-{palette}.png.
 * Если файл удалён, Monster.renderPart() рисует часть процедурно (фолбэк).
 *
 * Всё целочисленное, CLDC 1.1, без float/Math и без аллокаций в кадре.
 */
public final class MonsterRig {

    // Порядок наложения = порядок в атласе.
    public static final int BODY = 0, SHADOW = 1, BACK = 2, LEGS = 3,
            ARM_L = 4, ARM_R = 5, TORSO = 6, HEAD = 7, FRONT = 8;
    public static final int PARTS = 9;
    public static final int VARIANTS = 6;
    public static final int CELL_W = 40, CELL_H = 56;
    public static final int DEATH_MS = 900;

    public static final String[] KIND = {
        "body", "shadow", "back", "legs", "armL", "armR",
        "torso", "head", "front"
    };

    /** Отдельные графические элементы (null = вариант без этой детали). */
    public final Raster[] piece = new Raster[PARTS];
    /** Базовая позиция каждой части внутри виртуального спрайта 40x56. */
    public final int[] bx = new int[PARTS], by = new int[PARTS];
    /** Текущее смещение части в пикселях виртуального спрайта. */
    public final int[] dx = new int[PARTS], dy = new int[PARTS];
    /** Скорость разлёта при смерти, пикселей виртуального спрайта в секунду. */
    public final int[] flyX = new int[PARTS], flyY = new int[PARTS];

    /** Фаза анимации конкретной особи, чтобы твари не двигались синхронно. */
    public int seed;

    public static MonsterRig build(Monster m) {
        MonsterRig r = new MonsterRig();
        int[] variant = {
            0, m.legs, m.extra, m.legs, m.arms, m.arms,
            m.torso, m.head, m.extra
        };
        for (int k = 0; k < PARTS; k++) {
            int v = variant[k];
            Raster p = Gfx.part(m.palette, k, v);
            if (p != null) {
                r.bx[k] = Gfx.partX(m.palette, k, v);
                r.by[k] = Gfx.partY(m.palette, k, v);
            } else {
                // Открытого PNG нет — рисуем ту же часть процедурно.
                p = Monster.renderPart(k, v, m.palette);
                r.bx[k] = 0;
                r.by[k] = 0;
            }
            if (p != null && p.vb == null) p.buildVb();
            r.piece[k] = p;
        }
        r.seed = (m.head * 31 + m.torso * 17 + m.arms * 7 +
                  m.legs * 3 + m.extra) * 13 + m.palette * 29;
        return r;
    }

    /** Сброс анимации: новая особь / возрождение / восстановление сейва. */
    public void reset() {
        for (int k = 0; k < PARTS; k++) {
            dx[k] = dy[k] = 0;
            flyX[k] = flyY[k] = 0;
        }
    }

    /**
     * Вычисляет смещения частей на момент now. Когти живут своей волной с
     * другим периодом, чем голова и спинные щупальца; тело и тень стоят.
     */
    public void animate(long now, Monster m) {
        if (m.dying) {
            long t = now - m.deathStart;
            if (t < 0) t = 0;
            if (t > DEATH_MS) t = DEATH_MS;
            int gravity = (int) ((long) 22 * t * t / 1000000L);
            for (int k = 0; k < PARTS; k++) {
                dx[k] = (int) ((long) flyX[k] * t / 1000L);
                dy[k] = (int) ((long) flyY[k] * t / 1000L) + gravity;
            }
            return;
        }
        for (int k = 0; k < PARTS; k++) dx[k] = dy[k] = 0;

        int tick = (int) (now / 40);
        int wave = (m.moving || m.fleeUntil > now) ? 5 : 3;
        boolean lunge = m.windupUntil != 0;
        // Разные частоты => левая и правая конечности НЕ повторяют друг друга.
        // beatL/beatR гарантируют, что даже в покое когти шевелятся каждый
        // кадр: фазы левой и правой конечности сдвинуты на один такт.
        int a = Fx.fixDegree(tick * 7 + seed);
        int b = Fx.fixDegree(tick * 11 + seed + 143);
        int beatL = (tick & 1) == 0 ? -2 : 2;
        int beatR = (tick & 2) == 0 ? 2 : -2;
        dx[ARM_L] = -(Fx.sin(a) * wave >> 12) + beatL;
        dy[ARM_L] = (Fx.cos(a) * wave >> 12) + ((tick & 1) == 0 ? 1 : -1);
        dx[ARM_R] = (Fx.sin(b) * wave >> 12) + beatR;
        dy[ARM_R] = -(Fx.cos(b) * wave >> 12) + ((tick & 2) == 0 ? -1 : 1);
        if (lunge) {
            // Замах: когти расходятся и выбрасываются вперёд/вверх.
            dx[ARM_L] -= 3;
            dx[ARM_R] += 3;
            dy[ARM_L] -= 7;
            dy[ARM_R] -= 7;
        }
        // Остальные компоненты тоже слегка двигаются независимо.
        dy[HEAD] = Fx.sin(Fx.fixDegree(tick * 5 + seed + 71)) >> 12;
        dy[BACK] = Fx.cos(b) >> 12;
        dy[FRONT] = Fx.sin(b) >> 12;
        dy[LEGS] = m.moving ? -(Fx.cos(a) >> 12) : 0;
    }

    /** Готовит детерминированные траектории разлёта девяти фрагментов. */
    public void startDeath(int deathSeed) {
        Rng r = new Rng(deathSeed | 1);
        for (int k = 0; k < PARTS; k++) {
            dx[k] = dy[k] = 0;
            flyX[k] = r.nextInt(161) - 80;          // -80..+80 px/с
            flyY[k] = -(18 + r.nextInt(74));        // вверх, потом гравитация
            if (k == BODY || k == SHADOW) {
                flyX[k] /= 2;                       // тяжёлое тело летит меньше
                flyY[k] = -12;
            }
            if (k == HEAD) flyY[k] -= 24;           // голова отскакивает выше
        }
    }

    /**
     * Склеивает модули в один обычный Raster (титульный экран, тесты,
     * экспорт открытых PNG). В игре рендерер так НЕ делает.
     */
    public Raster snapshot() {
        Raster out = new Raster(CELL_W, CELL_H);
        for (int k = 0; k < PARTS; k++) {
            Raster p = piece[k];
            if (p == null) continue;
            for (int j = 0; j < p.h; j++) {
                int yy = by[k] + j;
                if (yy < 0 || yy >= CELL_H) continue;
                for (int i = 0; i < p.w; i++) {
                    int c = p.p[j * p.w + i];
                    if ((c >>> 24) == 0) continue;
                    int xx = bx[k] + i;
                    if (xx < 0 || xx >= CELL_W) continue;
                    out.p[yy * CELL_W + xx] = c;
                }
            }
        }
        out.buildVb();
        return out;
    }

    public int visibleParts() {
        int n = 0;
        for (int k = 0; k < PARTS; k++) if (piece[k] != null) n++;
        return n;
    }
}
