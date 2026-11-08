package samosbor;

/**
 * Рейкастинг-рендерер: DDA-лучи, текстурированные стены/пол/потолок,
 * z-buffer, туман Самосбора и билборд-спрайты.
 * Алгоритм DDA целиком основан на game.World.java из приложенного
 * движка Mecasting (Роман Лагин), переписан под процедурные карты.
 */
public final class Renderer {

    private static final int FP = Fx.FP;       // масштаб: 4096 = одна клетка
    private static final int SHIFT = Fx.fp;    // сдвиг для fixed point: 12 бит
    private static final int FPSQR = Fx.FPSQR;
    private static final int FOG_NORMAL = 0xff2a1036;
    private static final int FOG_VB = 0xff200000;

    public final int sw, sh, fullH;
    public final int[] screen;
    private final int[] zBuffer;
    private final int[] yToDistance;
    private final int[] fogRow;
    private final int[] deltaDiv;
    private final Texture[] tex;

    public boolean vbMode;
    private int fogColor = FOG_NORMAL;

    // Попадания по прицелу: прямоугольники монстров в экранных координатах.
    public final int[] sprLeft = new int[32], sprTop = new int[32],
            sprRight = new int[32], sprBottom = new int[32],
            sprIndex = new int[32], sprDepth = new int[32];
    public int sprCount;

    public final Raster[] itemSprite = new Raster[7];

    public Renderer(int width, int fullHeight, int viewHeight, Texture[] textures) {
        sw = width;
        fullH = fullHeight;
        sh = viewHeight;
        tex = textures;
        screen = new int[sw * fullH];
        zBuffer = new int[sw];
        yToDistance = new int[sh];
        fogRow = new int[sh];
        deltaDiv = new int[16384];

        for (int y = sh / 2 + 1; y < sh; y++) {
            yToDistance[y] = (sh * FP) / (2 * y - sh);
        }
        yToDistance[sh / 2] = yToDistance[sh / 2 + 1];
        for (int y = 0; y < sh / 2; y++) yToDistance[y] = yToDistance[sh - 1 - y];
        for (int y = 0; y < sh; y++) {
            fogRow[y] = fogByDist(yToDistance[y]);
        }
        for (int i = 0; i < deltaDiv.length; i++) {
            if (i != 8192) deltaDiv[i] = FPSQR / (i - 8192);
        }

        for (int t = 1; t < itemSprite.length; t++) {
            itemSprite[t] = makeItemSprite(t);
            itemSprite[t].buildVb();
        }
    }

    public void setVbMode(boolean on) {
        vbMode = on;
        fogColor = on ? FOG_VB : FOG_NORMAL;
    }

    private static int fogByDist(long d) {
        // Туман наступает на 25% быстрее, чем в первой версии: дальний конец
        // зала тонет во тьме, широкий проход читается как тесный — как в
        // сумрачных интерьерах Insmouse no Yakata. Яркость сохранена, чтобы
        // была видна фотографическая фактура бетона и ржавчины.
        int f = 256 - (int) ((d + (d >> 2)) >> 8);
        if (f < 40) f = 40;
        if (f > 256) f = 256;
        return f;
    }

    /** Быстрое затухание в цвет тумана (упакованные каналы, без делений). */
    private static int shade(int c, int f, int fog) {
        int inv = 256 - f;
        return 0xff000000 |
                (((((c & 0xFF00FF) * f + (fog & 0xFF00FF) * inv) & 0xFF00FF00) |
                        (((c & 0x00FF00) * f + (fog & 0x00FF00) * inv) & 0x00FF0000)) >>> 8);
    }

    // ------------------------------------------------------------------ кадр

    public void render(Level lv, int posX, int posY,
                       int dirX, int dirY, int planeX, int planeY, long now) {
        sprCount = 0;
        int horizon = sh >> 1;
        int cameraDeltaX = 8192 / sw;
        int cameraX = -cameraDeltaX / 2 * sw;
        long det = (long) dirX * planeY - (long) dirY * planeX;
        if (det == 0) det = 1;

        for (int x = 0; x < sw; x++, cameraX += cameraDeltaX) {
            int rayDirX = dirX + (planeX * cameraX >> SHIFT);
            int rayDirY = dirY + (planeY * cameraX >> SHIFT);
            if (rayDirX == 0) rayDirX = 1;
            if (rayDirY == 0) rayDirY = 1;

            int ix = 8192 + rayDirX;
            int iy = 8192 + rayDirY;
            if (ix < 0) ix = 0; else if (ix > 16383) ix = 16383;
            if (iy < 0) iy = 0; else if (iy > 16383) iy = 16383;

            int ddXNorm = deltaDiv[ix];
            int ddYNorm = deltaDiv[iy];
            int ddX = ddXNorm < 0 ? -ddXNorm : ddXNorm;
            int ddY = ddYNorm < 0 ? -ddYNorm : ddYNorm;

            int mapX = posX & 0xfffff000;
            int mapY = posY & 0xfffff000;
            int sideDistX, sideDistY, stepX, stepY, stepDistX = 0, stepDistY = 0;

            if (rayDirX < 0) {
                stepX = -FP;
                stepDistX = FP;
                sideDistX = -((posX & 4095) << SHIFT) / rayDirX;
            } else {
                stepX = FP;
                sideDistX = ((mapX + FP - posX) << SHIFT) / rayDirX;
            }
            if (rayDirY < 0) {
                stepY = -FP;
                stepDistY = FP;
                sideDistY = -((posY & 4095) << SHIFT) / rayDirY;
            } else {
                stepY = FP;
                sideDistY = ((mapY + FP - posY) << SHIFT) / rayDirY;
            }

            boolean side = false;
            int cell = 0;
            int guard = 0;
            while (guard++ < 128) {
                if (sideDistX < sideDistY) {
                    sideDistX += ddX;
                    mapX += stepX;
                    side = false;
                } else {
                    sideDistY += ddY;
                    mapY += stepY;
                    side = true;
                }
                int cx = mapX >> 12, cy = mapY >> 12;
                if (cx < 0) cx = 0; else if (cx >= lv.w) cx = lv.w - 1;
                if (cy < 0) cy = 0; else if (cy >= lv.h) cy = lv.h - 1;
                cell = cx + cy * lv.w;
                lv.seen[cell] = true;
                if (lv.tile[cell] != Level.FLOOR) break;
            }

            int perpWallDist;
            if (!side) perpWallDist = (int) (((long) (mapX - posX + stepDistX) * ddXNorm) >> SHIFT);
            else perpWallDist = (int) (((long) (mapY - posY + stepDistY) * ddYNorm) >> SHIFT);
            if (perpWallDist < 64) perpWallDist = 64;
            zBuffer[x] = perpWallDist;
            int perpNorm = FPSQR / perpWallDist;

            int lineHeight = (int) (((long) sh * perpNorm) >> SHIFT);
            if (lineHeight < 1) lineHeight = 1;

            Texture wall = tex[Level.TILE_TEX[lv.tile[cell]] & 0xff];
            int[] wp = vbMode ? wall.vb : wall.rgb;
            int texYDelta = (wall.h << 15) / lineHeight;
            int texY = 0;
            int drawStart = horizon - (lineHeight >> 1);
            int drawEnd = horizon + (lineHeight >> 1);
            if (drawStart < 0) {
                texY = -drawStart * texYDelta;
                drawStart = 0;
            }
            if (drawEnd >= sh) drawEnd = sh - 1;

            int wallX;
            if (!side) wallX = (int) (((long) posY + (((long) perpWallDist * rayDirY) >> SHIFT)) & 4095);
            else wallX = (int) (((long) posX + (((long) perpWallDist * rayDirX) >> SHIFT)) & 4095);
            int texX = ((wallX << wall.wBits) >> SHIFT) & wall.wLen;
            if ((!side && rayDirX < 0) || (side && rayDirY > 0)) texX = wall.w - texX - 1;

            int f = fogByDist(perpWallDist);
            if (side && f > 30) f -= 24;
            int[] buf = screen;
            for (int y = drawStart; y <= drawEnd; y++) {
                int ty = texY >> 15;
                if (ty < 0) ty = 0; else if (ty >= wall.h) ty = wall.h - 1;
                int col = wp[ty * wall.w + texX];
                buf[y * sw + x] = shade(col, f, fogColor);
                texY += texYDelta;
            }

            // Пол / потолок (интерполяция из движка Mecasting).
            int floorXWall, floorYWall;
            if (!side && rayDirX > 0) {
                floorXWall = mapX;
                floorYWall = mapY + wallX;
            } else if (!side && rayDirX < 0) {
                floorXWall = mapX + FP;
                floorYWall = mapY + wallX;
            } else if (side && rayDirY > 0) {
                floorXWall = mapX + wallX;
                floorYWall = mapY;
            } else {
                floorXWall = mapX + wallX;
                floorYWall = mapY + FP;
            }
            for (int y = drawEnd + 1; y < sh; y++) {
                int weight = (int) (((long) yToDistance[y] * perpNorm) >> SHIFT);
                if (weight > FP) weight = FP;
                if (weight < 0) weight = 0;
                int fx = (int) (((long) weight * floorXWall + (long) (FP - weight) * posX) >> SHIFT);
                int fy = (int) (((long) weight * floorYWall + (long) (FP - weight) * posY) >> SHIFT);
                int cm = (fx >> 12) + (fy >> 12) * lv.w;
                if (cm < 0) cm = 0; else if (cm >= lv.tile.length) cm = lv.tile.length - 1;

                Texture ft = tex[lv.floorTex[cm] & 0xff];
                int[] fp = vbMode ? ft.vb : ft.rgb;
                int txf = ((fx << ft.wBits) >> SHIFT) & ft.wLen;
                int tyf = ((fy << ft.hBits) >> SHIFT) & ft.hLen;
                int col = fp[tyf * ft.w + txf];
                int ff = fogRow[y];
                buf[y * sw + x] = shade(col, ff, fogColor);

                Texture ct = tex[lv.ceilTex[cm] & 0xff];
                int[] cp = vbMode ? ct.vb : ct.rgb;
                int ccol = cp[tyf * ct.w + txf];
                int cy2 = sh - y - 1;
                if (cy2 >= 0) buf[cy2 * sw + x] = shade(ccol, ff > 60 ? ff - 30 : 40, fogColor);
            }
        }

        // Предметы
        for (int i = 0; i < lv.itemCount; i++) {
            int t = lv.itemType[i];
            if (t <= 0 || t >= itemSprite.length) continue;
            int wx = lv.itemX[i] * FP + FP / 2;
            int wy = lv.itemY[i] * FP + FP / 2;
            int bob = (int) ((now / 12) & 7);
            if (!lv.seen[lv.idx(lv.itemX[i], lv.itemY[i])]) continue;
            drawBillboard(itemSprite[t], wx, wy, 1300 + bob * 24, det,
                    posX, posY, dirX, dirY, planeX, planeY, -1, horizon);
        }

        // Монстры
        if (lv.monsters != null) {
            for (int i = 0; i < lv.monsterCount; i++) {
                Monster m = lv.monsters[i];
                if (!m.alive) continue;
                int cx = m.x >> 12, cy = m.y >> 12;
                if (!lv.inside(cx, cy) || !lv.seen[lv.idx(cx, cy)]) continue;
                // Поза выпада/отпрыгивания видна заранее — удар телеграфируется.
                int frame = (m.windupUntil != 0 || m.fleeUntil > now)
                        ? 1 : (int) ((now / 520) & 1);
                m.frameIndex = frame;
                Raster spr = m.frames[frame];
                drawBillboard(spr, m.x, m.y, 3500, det,
                        posX, posY, dirX, dirY, planeX, planeY, i, horizon);
            }
        }
    }

    private void drawBillboard(Raster spr, int wx, int wy, int heightFP, long det,
                               int posX, int posY, int dirX, int dirY,
                               int planeX, int planeY, int monsterIndex, int horizon) {
        long sx = (long) wx - posX;
        long sy = (long) wy - posY;
        long ty = (((long) sx * planeY - (long) sy * planeX) * FP) / det;
        long tx = (((long) dirX * sy - (long) dirY * sx) * FP) / det;
        if (ty <= 80 || ty > 2000000L) return;

        int screenX = (sw >> 1) + (int) (((long) (sw >> 1) * tx) / ty);
        int lineH = (int) ((long) sh * FP / ty);
        int spriteH = (int) ((long) lineH * heightFP >> SHIFT);
        if (spriteH < 2) return;
        int spriteW = spriteH * spr.w / spr.h;
        if (spriteW < 1) return;
        int left = screenX - (spriteW >> 1);
        int top = horizon + (lineH >> 1) - spriteH;
        if (left >= sw || left + spriteW < 0 || top >= sh || top + spriteH < 0) return;

        int f = fogByDist(ty);
        int[] pix = (vbMode && spr.vb != null) ? spr.vb : spr.p;
        boolean drawn = false;
        for (int x = left; x < left + spriteW; x++) {
            if (x < 0 || x >= sw) continue;
            if (ty >= (long) zBuffer[x]) continue;
            drawn = true;
            int tX = (x - left) * spr.w / spriteW;
            if (tX < 0 || tX >= spr.w) continue;
            for (int y = top; y < top + spriteH; y++) {
                if (y < 0 || y >= sh) continue;
                int tY = (y - top) * spr.h / spriteH;
                int c = pix[tY * spr.w + tX];
                if ((c >>> 24) == 0) continue;
                screen[y * sw + x] = shade(c, f, fogColor);
            }
        }
        if (monsterIndex >= 0 && drawn && sprCount < sprLeft.length) {
            sprLeft[sprCount] = left;
            sprTop[sprCount] = top;
            sprRight[sprCount] = left + spriteW;
            sprBottom[sprCount] = top + spriteH;
            sprIndex[sprCount] = monsterIndex;
            sprDepth[sprCount] = (int) ty;
            sprCount++;
        }
    }

    // ------------------------------------------------------- спрайты предметов

    private Raster makeItemSprite(int type) {
        Raster g = new Raster(24, 24);
        int black = 0xff101018;
        int steel = 0xff9a9ca0;
        switch (type) {
            case Level.ITEM_KEY: // пропуск Партии
                g.rect(4, 7, 16, 11, black);
                g.rect(5, 8, 14, 9, 0xffd8cf40);
                g.rect(5, 8, 14, 2, 0xffa02222);
                g.rect(7, 12, 6, 4, steel);
                g.rect(15, 12, 3, 3, black);
                g.ellipse(12, 3, 3, 2, 0xffd8cf40);
                break;
            case Level.ITEM_WHITE: // белый шар — открывает карту
                g.ellipse(12, 12, 9, 9, 0xff6b6c70);
                g.ellipse(12, 12, 7, 7, 0xfff0e6d0);
                g.ellipse(10, 10, 3, 3, 0xffffffff);
                g.ring(12, 12, 10, 10, 80, 0xffffffff);
                break;
            case Level.ITEM_BLACK: // чёрный шар — показывает предметы
                g.ellipse(12, 12, 9, 9, 0xff611c6d);
                g.ellipse(12, 12, 7, 7, 0xff1a1022);
                g.ellipse(12, 12, 3, 3, 0xffb952c7);
                g.ring(12, 12, 10, 10, 80, 0xffe08ae8);
                break;
            case Level.ITEM_AMMO: // патроны
                for (int i = 0; i < 3; i++) {
                    int x = 4 + i * 6;
                    g.rect(x, 9, 4, 11, 0xff85532c);
                    g.rect(x, 9, 4, 3, 0xffd8cf40);
                    g.rect(x + 1, 12, 2, 6, 0xffb0753a);
                }
                break;
            case Level.ITEM_MED: // пищконцентрат
                g.rect(4, 7, 16, 11, black);
                g.rect(5, 8, 14, 9, 0xffc0b090);
                g.rect(10, 9, 4, 7, 0xffa02222);
                g.rect(8, 11, 8, 3, 0xffa02222);
                break;
            case Level.ITEM_CLOCK: // +20 секунд до Самосбора
                g.ellipse(12, 12, 9, 9, black);
                g.ellipse(12, 12, 8, 8, 0xfff0e6d0);
                g.line(12, 12, 12, 7, black, 1);
                g.line(12, 12, 16, 13, black, 1);
                g.ellipse(12, 12, 1, 1, black);
                break;
            default:
                break;
        }
        // Предметы тоже получают настоящую фактуру (сталь / слизь).
        boolean orb = (type == Level.ITEM_WHITE || type == Level.ITEM_BLACK);
        g.photoGrain(tex[orb ? 12 : 3], 54);
        return g;
    }
}
