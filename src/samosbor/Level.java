package samosbor;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * ПРОЦЕДУРНЫЙ ЭТАЖ ГИГАХРУЩЁВКИ.
 * Коридоры шириной РОВНО ОДНУ клетку: randomized DFS по сетке узлов с шагом
 * GRID=5, между узлами выкапывается одноклеточный проход. Ширину коридору
 * даёт КВАДРАТНЫЙ тайл 1:1 в проекции рендерера (planeLen = FP*w/(2*viewH)),
 * а не вторая клетка пола. В четырёхклеточных простенках между коридорами
 * стоят квартиры гигахрущёвки: комната 3x2 за обычной дверью DOOR, которую
 * открывают толчком. Плюс минимум петель, гермодверь, ключ-пропуск, белый/
 * чёрный шары, патроны и твари из частей тела.
 * Всё детерминировано seed'ом: снимок RMS восстанавливает ровно тот же этаж.
 */
public final class Level {

    // Типы клеток. FLOOR и DOOR_OPEN — проходимый пол; DOOR — закрытая
    // дверь (твёрдая для луча и игрока, но «прозрачная» для BFS/ИИ).
    public static final byte FLOOR = 0, WALL = 1, PAINT = 2, TILEWALL = 3,
            APART = 4, WINDOW = 5, POSTER = 6, PIPES = 7, CRACKED = 8,
            GRATE = 9, BRICK = 10, KLET = 11, EXIT = 12, LIFT = 13,
            DOOR = 14, DOOR_OPEN = 15;

    // Номер текстуры для каждого типа стены (см. res/textures).
    // DOOR -> dver.png (4), DOOR_OPEN -> pol.png (10) — луч его не задевает.
    public static final byte[] TILE_TEX = {
        0, 0, 1, 2, 4, 5, 6, 7, 8, 13, 15, 14, 3, 9, 4, 10
    };

    // Предметы
    public static final int ITEM_KEY = 1, ITEM_WHITE = 2, ITEM_BLACK = 3,
            ITEM_AMMO = 4, ITEM_MED = 5, ITEM_CLOCK = 6;

    public int w, h;
    public byte[] tile;
    public byte[] floorTex, ceilTex;
    public boolean[] seen;
    public int[] dist;

    public int startX, startY;
    public int exitX, exitY;          // клетка гермодвери (стена)
    public int approachX, approachY;  // клетка, с которой её открывают
    public int keyX, keyY;
    public int maxDist;

    public int[] itemType, itemX, itemY;
    public int itemCount;

    public Monster[] monsters;
    public int monsterCount;

    public long seed;
    public int floorNumber;
    public int stage;
    public int timeLimitSec;
    public Rng rng;

    private int[] queue;
    private int gridCols, gridRows;   // число узлов-перекрёстков по осям

    public Level(long seed, int floorNumber, int stage) {
        this.seed = seed;
        this.floorNumber = floorNumber;
        this.stage = stage;
        rng = new Rng(seed ^ (0x9e3779b9L * (floorNumber + 1)) ^ (stage * 0x85ebca6bL));

        w = 15 + 2 * (3 + rng.nextInt(3) + stage / 5);
        h = 15 + 2 * (3 + rng.nextInt(3) + stage / 5);
        if (w > 31) w = 31;
        if (h > 31) h = 31;

        int n = w * h;
        tile = new byte[n];
        floorTex = new byte[n];
        ceilTex = new byte[n];
        seen = new boolean[n];
        dist = new int[n];
        queue = new int[n];
        itemType = new int[32];
        itemX = new int[32];
        itemY = new int[32];

        for (int i = 0; i < n; i++) tile[i] = WALL;

        carveMaze();          // одноклеточные коридоры
        carveLoops();
        assignTextures();
        chooseStart();
        connectAll();
        computeDist(startX, startY);
        placeExitAndKey();    // гермодверь и пропуск — только в коридоре
        carveApartments();    // квартиры за обычными дверями
        computeDist(startX, startY);   // dist учитывает квартиры за дверями
        placeItems();
        placeMonsters();

        timeLimitSec = 55 + maxDist + (maxDist / 2) + monsterCount;
        if (timeLimitSec < 70) timeLimitSec = 70;
        if (timeLimitSec > 210) timeLimitSec = 210;
    }

    public int idx(int x, int y) {
        return x + y * w;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /**
     * Проходимость для BFS/ИИ: закрытая дверь считается проходимой, чтобы
     * твари могли подойти к ней и открыть, а все клетки этажа оставались
     * связными. Для луча и шага игрока см. isBlocking().
     */
    public boolean isWall(int x, int y) {
        if (!inside(x, y)) return true;
        int t = tile[idx(x, y)] & 0xff;
        return t != FLOOR && t != DOOR_OPEN && t != DOOR;
    }

    /** Твёрдость для рейкастера и игрока: закрытая дверь ещё не открыта. */
    public boolean isBlocking(int x, int y) {
        if (!inside(x, y)) return true;
        int t = tile[idx(x, y)] & 0xff;
        return t != FLOOR && t != DOOR_OPEN;
    }

    /** Луч проходит только сквозь пол и уже открытую дверь. */
    public static boolean isTransparent(int t) {
        return t == FLOOR || t == DOOR_OPEN;
    }

    public boolean isDoor(int x, int y) {
        return inside(x, y) && tile[idx(x, y)] == DOOR;
    }

    /** Толчком дверь распахивается и становится сквозной. */
    public boolean openDoor(int x, int y) {
        if (!inside(x, y)) return false;
        int i = idx(x, y);
        if (tile[i] != DOOR) return false;
        tile[i] = DOOR_OPEN;
        floorTex[i] = 10;
        ceilTex[i] = 11;
        seen[i] = true;
        return true;
    }

    public boolean isBreakable(int x, int y) {
        return inside(x, y) && tile[idx(x, y)] == CRACKED;
    }

    // ---------------------------------------------------------------- лабиринт

    // Коридоры идут по линиям сетки с шагом GRID и остаются шириной РОВНО
    // В ОДНУ КЛЕТКУ. Между линиями остаётся GRID-1 = 4 клетки простенка —
    // ровно столько, чтобы вместить квартиру 3x2 с дверью и оставить по
    // одной стене с каждой стороны. Ширину виду даёт квадратный тайл 1:1
    // в рендерере (Game.planeLen), а не вторая клетка пола.
    private static final int GRID = 5;

    private int nodeX(int i) { return 1 + i * GRID; }
    private int nodeY(int j) { return 1 + j * GRID; }

    private void carveMaze() {
        gridCols = (w - 2) / GRID + 1;
        gridRows = (h - 2) / GRID + 1;
        if (gridCols < 2) gridCols = 2;
        if (gridRows < 2) gridRows = 2;
        boolean[] visited = new boolean[gridCols * gridRows];
        int[] stack = new int[gridCols * gridRows];
        int sp = 0;
        tile[idx(nodeX(0), nodeY(0))] = FLOOR;
        visited[0] = true;
        stack[sp++] = 0;

        int[] dirs = new int[4];
        while (sp > 0) {
            int cur = stack[sp - 1];
            int ci = cur % gridCols, cj = cur / gridCols;
            for (int i = 0; i < 4; i++) dirs[i] = i;
            for (int i = 3; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = dirs[i]; dirs[i] = dirs[j]; dirs[j] = t;
            }
            boolean moved = false;
            for (int i = 0; i < 4; i++) {
                int d = dirs[i];
                int ni = ci + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                int nj = cj + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                if (ni < 0 || nj < 0 || ni >= gridCols || nj >= gridRows) continue;
                int n = ni + nj * gridCols;
                if (visited[n]) continue;
                carveCorridor(ci, cj, ni, nj);
                visited[n] = true;
                stack[sp++] = n;
                moved = true;
                break;
            }
            if (!moved) sp--;
        }
    }

    /** Проход между соседними перекрёстками — ширина ровно одна клетка. */
    private void carveCorridor(int i0, int j0, int i1, int j1) {
        int x = nodeX(i0), y = nodeY(j0);
        int sx = i1 > i0 ? 1 : (i1 < i0 ? -1 : 0);
        int sy = j1 > j0 ? 1 : (j1 < j0 ? -1 : 0);
        for (int k = 0; k <= GRID; k++) {
            int cx = x + sx * k, cy = y + sy * k;
            if (cx > 0 && cy > 0 && cx < w - 1 && cy < h - 1) {
                tile[idx(cx, cy)] = FLOOR;
            }
        }
    }

    private void carveLoops() {
        // Редко соединяем соседние перекрёстки, которые DFS обошёл: петли
        // тоже одноклеточные, но этаж перестаёт быть чистым деревом.
        for (int j = 0; j < gridRows; j++) {
            for (int i = 0; i < gridCols; i++) {
                if (i + 1 < gridCols && rng.chance(7)) carveCorridor(i, j, i + 1, j);
                if (j + 1 < gridRows && rng.chance(7)) carveCorridor(i, j, i, j + 1);
            }
        }
    }

    /**
     * Квартиры гигахрущёвки: в четырёхклеточном простенке между коридорами
     * выкапывается комната 3x2, единственный вход — обычная дверь DOOR.
     * Вокруг комнаты обязательно остаётся рамка стен, иначе получится не
     * квартира, а сквозной проход.
     */
    private void carveApartments() {
        int wanted = 4 + rng.nextInt(4);       // 4..7 квартир на этаж
        boolean[] claimed = new boolean[tile.length];
        final int depth = 2, half = 1;         // 2 вглубь, 3 вширь
        for (int tries = 0; tries < 1600 && wanted > 0; tries++) {
            int cx = 1 + rng.nextInt(w - 2), cy = 1 + rng.nextInt(h - 2);
            if (tile[idx(cx, cy)] != FLOOR) continue;
            int d = rng.nextInt(4);
            int dx = d == 0 ? 1 : (d == 1 ? -1 : 0);
            int dy = d == 2 ? 1 : (d == 3 ? -1 : 0);
            int px = -dy, py = dx;             // перпендикулярь коридору
            int wx = cx + dx, wy = cy + dy;    // клетка двери
            if (wx <= 0 || wy <= 0 || wx >= w - 1 || wy >= h - 1) continue;

            boolean ok = true;
            for (int k = 0; k <= depth + 1 && ok; k++) {
                for (int j = -half - 1; j <= half + 1; j++) {
                    int x = wx + dx * k + px * j;
                    int y = wy + dy * k + py * j;
                    if (x <= 0 || y <= 0 || x >= w - 1 || y >= h - 1) { ok = false; break; }
                    int i = idx(x, y);
                    byte t = tile[i];
                    if (t == FLOOR || t == EXIT || t == DOOR || t == DOOR_OPEN ||
                            claimed[i]) { ok = false; break; }
                }
            }
            if (!ok) continue;

            for (int k = 1; k <= depth; k++) {
                for (int j = -half; j <= half; j++) {
                    int i = idx(wx + dx * k + px * j, wy + dy * k + py * j);
                    openCell(i);
                    if (rng.chance(18)) floorTex[i] = 2;   // кафель в «ванной»
                    claimed[i] = true;
                }
            }
            int di = idx(wx, wy);
            tile[di] = DOOR;
            floorTex[di] = 10;
            ceilTex[di] = 11;
            claimed[di] = true;
            wanted--;
        }
    }

    private void assignTextures() {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = idx(x, y);
                if (tile[i] == FLOOR) {
                    floorTex[i] = (byte) (rng.chance(6 + stage) ? 12 : (rng.chance(12) ? 2 : 10));
                    ceilTex[i] = (byte) (rng.chance(10) ? 0 : 11);
                    continue;
                }
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    tile[i] = rng.chance(20) ? BRICK : WALL;
                    continue;
                }
                int roll = rng.nextInt(100);
                byte t;
                if (roll < 38) t = WALL;       // глухой бетон доминирует
                else if (roll < 51) t = PAINT;
                else if (roll < 59) t = TILEWALL;
                else if (roll < 73) t = APART; // двери квартир по обе стороны коридора
                else if (roll < 78) t = WINDOW;
                else if (roll < 82) t = POSTER;
                else if (roll < 86) t = PIPES;
                else if (roll < 89) t = GRATE;
                else if (roll < 92) t = BRICK;
                else if (roll < 93) t = KLET;
                else if (roll < 94) t = LIFT;
                else t = CRACKED; // ~6%: «B» в картах ROM — стены, что ломают твари
                tile[i] = t;
            }
        }
    }

    private void chooseStart() {
        // Самая «верхняя-левая» проходимая клетка — вход на этаж.
        int best = -1, bestScore = 1 << 30;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (tile[idx(x, y)] != FLOOR) continue;
                int score = x * x + y * y + rng.nextInt(4);
                if (score < bestScore) { bestScore = score; best = idx(x, y); }
            }
        }
        if (best < 0) { best = idx(1, 1); tile[best] = FLOOR; }
        startX = best % w;
        startY = best / w;
    }

    /** BFS от игрока; dist = число шагов, -1 если клетка недостижима. */
    public void computeDist(int sx, int sy) {
        for (int i = 0; i < dist.length; i++) dist[i] = -1;
        if (isWall(sx, sy)) return;
        int head = 0, tail = 0;
        int s = idx(sx, sy);
        dist[s] = 0;
        queue[tail++] = s;
        maxDist = 0;
        while (head < tail) {
            int cur = queue[head++];
            int x = cur % w, y = cur / w;
            int d = dist[cur] + 1;
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                int ny = y + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                if (isWall(nx, ny)) continue;
                int ni = idx(nx, ny);
                if (dist[ni] >= 0) continue;
                dist[ni] = d;
                if (d > maxDist) maxDist = d;
                queue[tail++] = ni;
            }
        }
    }

    /** Добиваемся связности: отрезанные залы пробиваются коридором к сети. */
    private void connectAll() {
        // Залов и петель больше, чем в одноклеточном лабиринте: проходов
        // должно хватать на каждый отрезанный карман, иначе часть этажа
        // останется недостижимой (проверяется в SelfTest на 60 этажах).
        for (int pass = 0; pass < w * h; pass++) {
            computeDist(startX, startY);
            int orphan = -1;
            for (int i = 0; i < tile.length; i++) {
                if (tile[i] == FLOOR && dist[i] < 0) { orphan = i; break; }
            }
            if (orphan < 0) return;
            int ox = orphan % w, oy = orphan / w;
            int nearest = -1, bestD = 1 << 30;
            for (int i = 0; i < tile.length; i++) {
                if (tile[i] != FLOOR || dist[i] < 0) continue;
                int dx = (i % w) - ox, dy = (i / w) - oy;
                int d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; nearest = i; }
            }
            if (nearest < 0) return;
            int nx = nearest % w, ny = nearest / w;
            int x = ox, y = oy;
            while (x != nx) {
                x += x < nx ? 1 : -1;
                if (x > 0 && x < w - 1) openCell(idx(x, y));
            }
            while (y != ny) {
                y += y < ny ? 1 : -1;
                if (y > 0 && y < h - 1) openCell(idx(x, y));
            }
        }
    }

    /** Пробитая перегородка становится полноценным полом с текстурой. */
    private void openCell(int i) {
        tile[i] = FLOOR;
        floorTex[i] = 10;
        ceilTex[i] = 11;
    }

    // ------------------------------------------------------------- расстановка

    private void placeExitAndKey() {
        computeDist(startX, startY);
        int bestD = -1;
        // гермодверь ставим в стену рядом с самой дальней клеткой
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = idx(x, y);
                if (tile[i] != FLOOR || dist[i] <= bestD) continue;
                for (int k = 0; k < 4; k++) {
                    int wx = x + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                    int wy = y + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                    if (!inside(wx, wy)) continue;
                    int wi = idx(wx, wy);
                    if (tile[wi] == FLOOR || wx == 0 || wy == 0 || wx == w - 1 || wy == h - 1)
                        continue;
                    bestD = dist[i];
                    exitX = wx; exitY = wy;
                    approachX = x; approachY = y;
                }
            }
        }
        if (bestD < 0) {
            exitX = w - 2; exitY = 1;
            approachX = w - 3; approachY = 1;
        }
        tile[idx(exitX, exitY)] = EXIT;

        // пропуск — далеко от старта, но не у самой двери
        int target = (bestD * 6) / 10;
        int bestDelta = 1 << 30;
        keyX = startX; keyY = startY;
        for (int i = 0; i < tile.length; i++) {
            if (tile[i] != FLOOR || dist[i] < 5) continue;
            int delta = Math.abs(dist[i] - target);
            if (delta < bestDelta) {
                bestDelta = delta;
                keyX = i % w; keyY = i / w;
            }
        }
        addItem(ITEM_KEY, keyX, keyY);
    }

    private void placeItems() {
        int ammoCount = 2 + rng.nextInt(3) + stage / 6;
        int medCount = 1 + rng.nextInt(2);
        int[] types = new int[ammoCount + medCount + 3];
        int t = 0;
        for (int i = 0; i < ammoCount; i++) types[t++] = ITEM_AMMO;
        for (int i = 0; i < medCount; i++) types[t++] = ITEM_MED;
        types[t++] = ITEM_CLOCK;
        if (rng.chance(70)) types[t++] = ITEM_WHITE;
        if (rng.chance(70)) types[t++] = ITEM_BLACK;
        for (int i = 0; i < t; i++) {
            int p = randomFloor(6);
            addItem(types[i], p % w, p / w);
        }
    }

    public int randomFloor(int minDist) {
        for (int tries = 0; tries < 300; tries++) {
            int x = 1 + rng.nextInt(w - 2);
            int y = 1 + rng.nextInt(h - 2);
            int i = idx(x, y);
            if (tile[i] != FLOOR || dist[i] < minDist) continue;
            if (x == keyX && y == keyY) continue;
            if (x == approachX && y == approachY) continue;
            boolean busy = false;
            for (int j = 0; j < itemCount; j++) {
                if (itemX[j] == x && itemY[j] == y) { busy = true; break; }
            }
            if (busy) continue;
            return i;
        }
        return idx(startX, startY);
    }

    private void addItem(int type, int x, int y) {
        if (itemCount >= itemType.length) return;
        itemType[itemCount] = type;
        itemX[itemCount] = x;
        itemY[itemCount] = y;
        itemCount++;
    }

    public int itemAt(int x, int y) {
        for (int i = 0; i < itemCount; i++) {
            if (itemX[i] == x && itemY[i] == y) return i;
        }
        return -1;
    }

    public void removeItem(int i) {
        if (i < 0 || i >= itemCount) return;
        itemCount--;
        itemType[i] = itemType[itemCount];
        itemX[i] = itemX[itemCount];
        itemY[i] = itemY[itemCount];
    }

    private void placeMonsters() {
        monsterCount = 4 + stage / 2;
        if (monsterCount > 12) monsterCount = 12;
        monsters = new Monster[monsterCount];
        for (int i = 0; i < monsterCount; i++) {
            Monster m = Monster.create(rng, stage);
            int p = randomFloor(8);
            m.place(p % w, p / w);
            m.nextThink = rng.nextInt(900);
            monsters[i] = m;
        }
    }

    /** Тварь проламывает треснувшую стену. */
    public boolean breakWall(int x, int y) {
        int i = idx(x, y);
        if (tile[i] != CRACKED) return false;
        tile[i] = FLOOR;
        floorTex[i] = (byte) (rng.chance(30) ? 12 : 10);
        ceilTex[i] = 11;
        seen[i] = true;
        return true;
    }

    public void revealAll() {
        for (int i = 0; i < seen.length; i++) seen[i] = true;
    }

    public void markSeen(int x, int y) {
        if (inside(x, y)) seen[idx(x, y)] = true;
    }

    /** Прямая видимость по клеткам (Брезенхем) — тварь должна ВИДЕТЬ игрока. */
    public boolean lineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        for (int guard = 0; guard < 96; guard++) {
            if (x == x1 && y == y1) return true;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
            if (x == x1 && y == y1) return true;
            if (isBlocking(x, y)) return false;   // закрытая дверь не прозрачна
        }
        return false;
    }

    // ------------------------------------------------------- автосохранение

    public void write(DataOutputStream out) throws Exception {
        out.writeLong(seed);
        out.writeInt(floorNumber);
        out.writeInt(stage);
        out.writeInt(w);
        out.writeInt(h);
        out.writeInt(timeLimitSec);
        out.writeInt(maxDist);
        out.writeInt(startX); out.writeInt(startY);
        out.writeInt(exitX); out.writeInt(exitY);
        out.writeInt(approachX); out.writeInt(approachY);
        out.writeInt(keyX); out.writeInt(keyY);
        out.write(tile, 0, tile.length);
        out.write(floorTex, 0, floorTex.length);
        out.write(ceilTex, 0, ceilTex.length);
        byte[] seenBytes = new byte[seen.length];
        for (int i = 0; i < seen.length; i++) seenBytes[i] = (byte) (seen[i] ? 1 : 0);
        out.write(seenBytes, 0, seenBytes.length);
        out.writeInt(itemCount);
        for (int i = 0; i < itemCount; i++) {
            out.writeShort(itemType[i]);
            out.writeShort(itemX[i]);
            out.writeShort(itemY[i]);
        }
        out.writeInt(monsterCount);
        for (int i = 0; i < monsterCount; i++) monsters[i].write(out);
    }

    public static Level restore(DataInputStream in) throws Exception {
        long seed = in.readLong();
        int floor = in.readInt();
        int stage = in.readInt();
        Level lv = new Level(seed, floor, stage);
        int w = in.readInt(), h = in.readInt();
        if (w != lv.w || h != lv.h) throw new Exception("save size mismatch");
        lv.timeLimitSec = in.readInt();
        lv.maxDist = in.readInt();
        lv.startX = in.readInt(); lv.startY = in.readInt();
        lv.exitX = in.readInt(); lv.exitY = in.readInt();
        lv.approachX = in.readInt(); lv.approachY = in.readInt();
        lv.keyX = in.readInt(); lv.keyY = in.readInt();
        in.readFully(lv.tile);
        in.readFully(lv.floorTex);
        in.readFully(lv.ceilTex);
        byte[] seenBytes = new byte[lv.seen.length];
        in.readFully(seenBytes);
        for (int i = 0; i < seenBytes.length; i++) lv.seen[i] = seenBytes[i] != 0;
        lv.itemCount = in.readInt();
        if (lv.itemCount > lv.itemType.length) {
            lv.itemType = new int[lv.itemCount];
            lv.itemX = new int[lv.itemCount];
            lv.itemY = new int[lv.itemCount];
        }
        for (int i = 0; i < lv.itemCount; i++) {
            lv.itemType[i] = in.readShort();
            lv.itemX[i] = in.readShort();
            lv.itemY[i] = in.readShort();
        }
        lv.monsterCount = in.readInt();
        lv.monsters = new Monster[lv.monsterCount];
        for (int i = 0; i < lv.monsterCount; i++) lv.monsters[i] = Monster.restore(in);
        lv.computeDist(lv.startX, lv.startY);
        return lv;
    }
}
