package samosbor;

/**
 * ПРОЦЕДУРНЫЙ ЭТАЖ ГИГАХРУЩЁВКИ.
 * Лабиринт (randomized DFS) + комнаты-«квартиры» + петли + гермодверь,
 * ключ-пропуск, белый/чёрный шары, патроны и твари, собранные из частей тела.
 * Всё детерминировано seed'ом: пароль восстанавливает ровно тот же этаж.
 */
public final class Level {

    // Типы клеток. Всё, что не FLOOR, — стена (двери в том числе).
    public static final byte FLOOR = 0, WALL = 1, PAINT = 2, TILEWALL = 3,
            APART = 4, WINDOW = 5, POSTER = 6, PIPES = 7, CRACKED = 8,
            GRATE = 9, BRICK = 10, KLET = 11, EXIT = 12, LIFT = 13;

    // Номер текстуры для каждого типа стены (см. res/textures).
    public static final byte[] TILE_TEX = {
        0, 0, 1, 2, 4, 5, 6, 7, 8, 13, 15, 14, 3, 9
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

        carveMaze();
        carveRooms();
        carveLoops();
        assignTextures();
        chooseStart();
        connectAll();
        computeDist(startX, startY);
        placeExitAndKey();
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

    public boolean isWall(int x, int y) {
        if (!inside(x, y)) return true;
        return tile[idx(x, y)] != FLOOR;
    }

    public boolean isBreakable(int x, int y) {
        return inside(x, y) && tile[idx(x, y)] == CRACKED;
    }

    // ---------------------------------------------------------------- лабиринт

    private void carveMaze() {
        int sp = 0;
        int[] stackX = new int[w * h];
        int[] stackY = new int[w * h];
        int x = 1, y = 1;
        tile[idx(x, y)] = FLOOR;
        stackX[sp] = x;
        stackY[sp] = y;
        sp++;

        int[] dirs = new int[4];
        while (sp > 0) {
            x = stackX[sp - 1];
            y = stackY[sp - 1];
            for (int i = 0; i < 4; i++) dirs[i] = i;
            for (int i = 3; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = dirs[i]; dirs[i] = dirs[j]; dirs[j] = t;
            }
            boolean moved = false;
            for (int i = 0; i < 4; i++) {
                int d = dirs[i];
                int dx = d == 0 ? 1 : (d == 1 ? -1 : 0);
                int dy = d == 2 ? 1 : (d == 3 ? -1 : 0);
                int nx = x + dx * 2, ny = y + dy * 2;
                if (nx <= 0 || ny <= 0 || nx >= w - 1 || ny >= h - 1) continue;
                if (tile[idx(nx, ny)] == FLOOR) continue;
                tile[idx(x + dx, y + dy)] = FLOOR;
                tile[idx(nx, ny)] = FLOOR;
                stackX[sp] = nx;
                stackY[sp] = ny;
                sp++;
                moved = true;
                break;
            }
            if (!moved) sp--;
        }
    }

    private void carveRooms() {
        int rooms = 3 + rng.nextInt(4);
        for (int r = 0; r < rooms; r++) {
            int rw = 3 + rng.nextInt(4);
            int rh = 3 + rng.nextInt(4);
            int x0 = 2 + rng.nextInt(w - rw - 4);
            int y0 = 2 + rng.nextInt(h - rh - 4);
            for (int y = y0; y < y0 + rh; y++) {
                for (int x = x0; x < x0 + rw; x++) tile[idx(x, y)] = FLOOR;
            }
        }
    }

    private void carveLoops() {
        for (int y = 2; y < h - 2; y++) {
            for (int x = 2; x < w - 2; x++) {
                if (tile[idx(x, y)] != WALL) continue;
                boolean horizontal = tile[idx(x - 1, y)] == FLOOR && tile[idx(x + 1, y)] == FLOOR;
                boolean vertical = tile[idx(x, y - 1)] == FLOOR && tile[idx(x, y + 1)] == FLOOR;
                if ((horizontal || vertical) && rng.chance(22)) tile[idx(x, y)] = FLOOR;
            }
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
                if (roll < 34) t = WALL;
                else if (roll < 49) t = PAINT;
                else if (roll < 58) t = TILEWALL;
                else if (roll < 68) t = APART;
                else if (roll < 75) t = WINDOW;
                else if (roll < 80) t = POSTER;
                else if (roll < 85) t = PIPES;
                else if (roll < 89) t = GRATE;
                else if (roll < 93) t = BRICK;
                else if (roll < 96) t = KLET;
                else if (roll < 98) t = LIFT;
                else t = CRACKED; // трещины могут проламывать твари
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

    /** Добиваемся связности: отрезанные комнаты пробиваются коридором к сети. */
    private void connectAll() {
        for (int pass = 0; pass < 14; pass++) {
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
                if (x > 0 && x < w - 1) tile[idx(x, y)] = FLOOR;
            }
            while (y != ny) {
                y += y < ny ? 1 : -1;
                if (y > 0 && y < h - 1) tile[idx(x, y)] = FLOOR;
            }
        }
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
        return true;
    }

    public void revealAll() {
        for (int i = 0; i < seen.length; i++) seen[i] = true;
    }

    public void markSeen(int x, int y) {
        if (inside(x, y)) seen[idx(x, y)] = true;
    }
}
