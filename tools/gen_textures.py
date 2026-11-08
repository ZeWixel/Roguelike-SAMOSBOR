#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Генератор текстур для «Гигахрущёвки».
Пишет 64x64 индексированные PNG (color type 3, 8 bit) — самый совместимый
с MIDP 2.0 / CLDC формат Image.createImage().

Запуск: python3 tools/gen_textures.py
"""
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   'res', 'textures')
SIZE = 64

# 64-цветная палитра: бетон, краска, ржавчина, металл, фиолет Самосбора.
PAL = [
    (11, 11, 16), (26, 26, 34), (43, 44, 51), (74, 75, 80),
    (107, 108, 112), (141, 142, 142), (182, 181, 173), (214, 212, 200),
    (24, 58, 43), (46, 91, 67), (74, 122, 93), (27, 52, 74),
    (44, 83, 112), (63, 107, 140), (85, 58, 34), (133, 83, 44),
    (176, 117, 58), (34, 34, 40), (60, 62, 70), (98, 101, 112),
    (154, 156, 160), (208, 200, 154), (255, 233, 168), (216, 207, 64),
    (160, 34, 34), (97, 28, 109), (139, 44, 155), (185, 82, 199),
    (224, 138, 232), (26, 16, 34), (51, 36, 60), (18, 24, 26),
    (37, 58, 68), (74, 106, 118), (110, 140, 146), (138, 124, 106),
    (180, 162, 134), (90, 90, 82), (154, 155, 146), (196, 197, 185),
    (42, 31, 24), (67, 50, 38), (17, 17, 20), (240, 230, 208),
    (192, 176, 144), (122, 31, 42), (184, 58, 54), (109, 118, 121),
    (164, 169, 167), (53, 74, 56), (129, 60, 138), (72, 44, 88),
    (39, 66, 62), (86, 108, 96), (222, 96, 72), (238, 168, 92),
    (142, 146, 150), (203, 199, 178), (28, 44, 52), (60, 82, 92),
    (204, 58, 62), (245, 206, 92), (94, 24, 60), (0, 0, 0),
]
while len(PAL) < 256:
    PAL.append((0, 0, 0))


class C:
    def __init__(self, seed):
        self.p = bytearray([2]) * (SIZE * SIZE)
        self.r = random.Random(seed)

    def set(self, x, y, c):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.p[y * SIZE + x] = c

    def get(self, x, y):
        return self.p[y * SIZE + x]

    def rect(self, x, y, w, h, c):
        for j in range(max(0, y), min(SIZE, y + h)):
            for i in range(max(0, x), min(SIZE, x + w)):
                self.p[j * SIZE + i] = c

    def line(self, x0, y0, x1, y1, c, thick=1):
        dx, dy = x1 - x0, y1 - y0
        n = max(abs(dx), abs(dy), 1)
        for k in range(n + 1):
            x = x0 + dx * k // n
            y = y0 + dy * k // n
            self.rect(x, y, thick, thick, c)

    def ellipse(self, cx, cy, rx, ry, c, fill=True):
        for y in range(max(0, cy - ry), min(SIZE, cy + ry + 1)):
            for x in range(max(0, cx - rx), min(SIZE, cx + rx + 1)):
                d = ((x - cx) * (x - cx)) / float(rx * rx) + \
                    ((y - cy) * (y - cy)) / float(ry * ry)
                if fill and d <= 1.0:
                    self.set(x, y, c)
                elif not fill and 0.72 <= d <= 1.0:
                    self.set(x, y, c)

    def noise(self, colors, count, x0=0, y0=0, w=SIZE, h=SIZE):
        for _ in range(count):
            x = x0 + self.r.randrange(max(1, w))
            y = y0 + self.r.randrange(max(1, h))
            self.set(x, y, self.r.choice(colors))

    def crack(self, x, y, length, c, dark):
        self.line(x, y, x + self.r.randint(-4, 4), y + length // 3, dark)
        x2 = x + self.r.randint(-6, 6)
        y2 = y + length // 3
        self.line(x2, y2, x2 + self.r.randint(-7, 7), y2 + length // 3, c)
        x3 = x2 + self.r.randint(-7, 7)
        y3 = y2 + length // 3
        self.line(x3, y3, x3 + self.r.randint(-8, 8), min(63, y3 + length // 3), dark)


def base_concrete(c, seed, tint=None):
    c.rect(0, 0, 64, 64, 3)
    # крупные панельные швы
    c.rect(0, 0, 64, 2, 1)
    c.rect(0, 62, 64, 2, 1)
    c.rect(0, 31, 64, 2, 1)
    c.rect(0, 0, 2, 64, 1)
    c.rect(62, 0, 2, 64, 1)
    c.rect(0, 33, 64, 1, 5)
    # потёртости и пятна
    c.noise([2, 3, 4, 5, 6, 39, 40], 520)
    for _ in range(7):
        x, y = c.r.randrange(2, 60), c.r.randrange(2, 60)
        c.ellipse(x, y, c.r.randint(2, 7), c.r.randint(1, 4), c.r.choice([2, 4, 39, 48]))
    for _ in range(3):
        c.crack(c.r.randrange(4, 58), c.r.randrange(4, 40), 18, 2, 1)
    if tint is not None:
        # тонировка поверх бетона (облупившаяся краска)
        for y in range(2, 62):
            for x in range(2, 62):
                if (x * 7 + y * 11 + seed * 3) % 17 < 11 and c.get(x, y) in (3, 4, 5, 6):
                    c.set(x, y, tint + (1 if (x + y) % 5 == 0 else 0))


def tex_beton():
    c = C(1)
    base_concrete(c, 1)
    c.noise([1, 2, 4, 5], 300)
    return c


def tex_kraska():
    c = C(2)
    base_concrete(c, 2, tint=8)
    # облупившиеся куски
    for _ in range(22):
        x, y = c.r.randrange(2, 58), c.r.randrange(2, 58)
        c.ellipse(x, y, c.r.randint(1, 5), c.r.randint(1, 3), c.r.choice([3, 4, 39]))
    # подтёки
    for _ in range(9):
        x = c.r.randrange(3, 60)
        c.line(x, 2, x, c.r.randint(8, 30), 7, 1)
    return c


def tex_plitka():
    c = C(3)
    c.rect(0, 0, 64, 64, 36)
    for gy in range(4):
        for gx in range(4):
            x, y = gx * 16, gy * 16
            col = 37 if (gx + gy) % 2 else 38
            c.rect(x + 1, y + 1, 14, 14, col)
            c.rect(x + 1, y + 1, 14, 1, 6)
            c.noise([36, 37, 38, 6, 39], 22, x + 1, y + 1, 14, 14)
    for _ in range(3):
        x, y = c.r.randrange(0, 4) * 16 + 3, c.r.randrange(0, 4) * 16 + 3
        c.line(x, y, x + 9, y + 11, 2)
    return c


def tex_germodver():
    c = C(4)
    c.rect(0, 0, 64, 64, 2)
    c.rect(2, 2, 60, 60, 17)
    c.rect(5, 5, 54, 54, 18)
    c.rect(8, 8, 48, 48, 16)
    # рёбра жёсткости
    c.rect(6, 6, 52, 2, 19)
    c.rect(6, 56, 52, 2, 19)
    c.rect(6, 6, 2, 52, 19)
    c.rect(56, 6, 2, 52, 19)
    # штурвал
    c.ellipse(32, 30, 13, 13, 19)
    c.ellipse(32, 30, 10, 10, 16)
    c.ellipse(32, 30, 3, 3, 19, True)
    for a in range(0, 360, 45):
        import math
        x = int(32 + 11 * math.cos(math.radians(a)))
        y = int(30 + 11 * math.sin(math.radians(a)))
        c.line(32, 30, x, y, 19)
    # сигнальная лампа и табличка
    c.rect(26, 46, 12, 6, 1)
    c.rect(28, 48, 8, 2, 44)
    c.ellipse(51, 14, 3, 3, 23)
    c.ellipse(51, 14, 1, 1, 55, True)
    # жёлто-чёрная разметка у порога
    for i in range(0, 64, 8):
        c.rect(i, 60, 4, 4, 23)
        c.rect(i + 4, 60, 4, 4, 23 if i % 16 else 22)
    # заклёпки
    for x in (7, 57):
        for y in (9, 30, 54):
            c.ellipse(x, y, 1, 1, 19, True)
    c.noise([15, 16, 18], 180)
    return c


def tex_dver():
    c = C(5)
    c.rect(0, 0, 64, 64, 1)
    c.rect(2, 1, 60, 63, 34)
    c.rect(4, 3, 56, 59, 13)
    c.rect(7, 6, 50, 24, 34)
    c.rect(7, 34, 50, 26, 34)
    c.rect(7, 6, 50, 1, 35)
    c.rect(7, 34, 50, 1, 35)
    c.rect(9, 8, 46, 20, 15)
    c.rect(9, 36, 46, 22, 15)
    c.rect(10, 9, 44, 18, 34)
    c.rect(10, 37, 44, 20, 34)
    c.noise([13, 15, 34, 35, 39], 260, 8, 4, 48, 56)
    # ручка, глазок, номер
    c.ellipse(52, 32, 3, 3, 19, True)
    c.rect(50, 31, 5, 2, 18)
    c.ellipse(32, 14, 2, 2, 41, True)
    c.rect(24, 20, 16, 8, 42)
    c.rect(26, 22, 12, 4, 1)
    return c


def tex_okno():
    c = C(6)
    base_concrete(c, 6)
    c.rect(8, 6, 48, 50, 1)
    c.rect(10, 8, 44, 46, 30)
    # бесконечные блоки за окном
    for k in range(5):
        x = 11 + k * 9 + (k % 2) * 2
        c.rect(x, 12 + (k % 3) * 3, 8, 40, 31 if k % 2 else 28)
        for j in range(5):
            for i in range(2):
                if (i + j + k) % 3 == 0:
                    c.rect(x + 1 + i * 3, 14 + j * 7, 2, 2, 20)
    c.rect(10, 8, 44, 1, 33)
    c.rect(31, 8, 2, 46, 17)
    c.rect(10, 30, 44, 2, 17)
    c.rect(6, 54, 52, 4, 4)
    c.rect(6, 54, 52, 1, 6)
    c.rect(8, 6, 48, 1, 19)
    return c


def tex_poster():
    c = C(7)
    base_concrete(c, 7)
    c.rect(6, 5, 52, 54, 1)
    c.rect(8, 7, 48, 50, 44)
    c.rect(8, 7, 48, 3, 45)
    # стилизованная эмблема Партии
    c.rect(14, 13, 36, 4, 42)
    c.rect(14, 13, 4, 20, 42)
    c.rect(46, 13, 4, 20, 42)
    c.rect(14, 31, 36, 3, 42)
    c.rect(27, 17, 10, 10, 45)
    for k in range(5):
        c.rect(12, 38 + k * 4, 40 - k * 3, 2, 42)
    c.rect(48, 50, 8, 7, 3)  # оторванный угол
    c.noise([1, 43, 44, 45], 300, 8, 7, 48, 50)
    return c


def tex_truby():
    c = C(8)
    base_concrete(c, 8)
    c.rect(6, 0, 8, 64, 46)
    c.rect(6, 0, 2, 64, 47)
    c.rect(18, 0, 5, 64, 16)
    c.rect(18, 0, 1, 64, 19)
    for y in (10, 32, 54):
        c.rect(4, y, 12, 3, 15)
        c.rect(4, y, 12, 1, 47)
    c.line(26, 0, 26, 64, 41)
    c.line(28, 0, 28, 64, 15)
    c.line(30, 0, 30, 64, 41)
    c.rect(36, 22, 26, 3, 46)
    c.rect(36, 25, 26, 1, 47)
    c.noise([13, 15, 46, 48], 120)
    return c


def tex_treshchina():
    c = C(9)
    base_concrete(c, 9)
    for k in range(4):
        x = 8 + k * 15
        c.crack(x, 4, 50, 25, 1)
        c.line(x, 4, x + 3, 20, 26)
        c.ellipse(x + 2, 28 + k * 3, 4, 2, 27)
    c.noise([24, 25, 26, 27, 29], 220)
    return c


def tex_lift():
    c = C(10)
    base_concrete(c, 10, tint=16)
    c.rect(10, 4, 44, 58, 17)
    c.rect(12, 6, 40, 54, 18)
    c.rect(31, 6, 2, 54, 1)
    c.rect(14, 8, 16, 50, 19)
    c.rect(34, 8, 16, 50, 19)
    c.rect(12, 26, 40, 2, 23)
    c.rect(24, 12, 16, 8, 1)
    c.rect(26, 14, 12, 4, 22)
    c.ellipse(32, 48, 4, 4, 25)
    c.ellipse(32, 48, 2, 2, 27, True)
    for i in range(0, 64, 8):
        c.rect(i, 60, 4, 4, 22 if (i // 8) % 2 else 41)
    c.noise([15, 16, 18, 19], 160)
    return c


def tex_pol():
    c = C(11)
    c.rect(0, 0, 64, 64, 39)
    for gy in range(2):
        for gx in range(2):
            x, y = gx * 32, gy * 32
            c.rect(x + 1, y + 1, 30, 30, 40 if (gx + gy) % 2 else 42)
            c.rect(x + 1, y + 1, 30, 1, 6)
    c.noise([2, 3, 39, 40, 42, 48], 620)
    for _ in range(6):
        c.line(c.r.randrange(64), c.r.randrange(64),
               c.r.randrange(64), c.r.randrange(64), 2)
    return c


def tex_potolok():
    c = C(12)
    base_concrete(c, 12)
    c.rect(0, 0, 64, 3, 1)
    c.rect(0, 61, 64, 3, 1)
    c.rect(0, 30, 64, 4, 16)
    c.rect(0, 30, 64, 1, 19)
    c.ellipse(32, 32, 12, 12, 19)
    c.ellipse(32, 32, 9, 9, 20)
    c.ellipse(32, 32, 6, 6, 21)
    c.ellipse(32, 32, 3, 3, 6, True)
    c.noise([2, 3, 4, 20, 48], 200)
    return c


def tex_sliz():
    c = C(13)
    c.rect(0, 0, 64, 64, 28)
    for _ in range(26):
        x, y = c.r.randrange(64), c.r.randrange(64)
        c.ellipse(x, y, c.r.randint(2, 10), c.r.randint(1, 6),
                  c.r.choice([24, 25, 29, 51]))
    for _ in range(10):
        c.ellipse(c.r.randrange(64), c.r.randrange(64), 2, 2, 26)
    c.noise([24, 25, 26, 27, 28, 29, 51], 420)
    return c


def tex_reshetka():
    c = C(14)
    c.rect(0, 0, 64, 64, 16)
    c.rect(3, 3, 58, 58, 1)
    for y in range(6, 59, 6):
        c.rect(5, y, 54, 3, 18)
        c.rect(5, y, 54, 1, 19)
    c.rect(3, 3, 58, 2, 15)
    c.rect(3, 59, 58, 2, 15)
    c.noise([13, 14, 15, 16, 18, 48], 300)
    return c


def tex_klet():
    c = C(15)
    c.rect(0, 0, 64, 64, 28)
    c.noise([24, 25, 29, 51, 52], 700)
    for _ in range(14):
        x = c.r.randrange(64)
        c.line(x, 0, x + c.r.randint(-12, 12), 64, 25, 2)
    for _ in range(7):
        c.ellipse(c.r.randrange(64), c.r.randrange(64), 5, 7, 26)
        c.ellipse(c.r.randrange(64), c.r.randrange(64), 2, 2, 27, True)
    return c


def tex_kirpich():
    c = C(16)
    c.rect(0, 0, 64, 64, 36)
    for gy in range(8):
        off = 0 if gy % 2 else 8
        for gx in range(-1, 5):
            x, y = gx * 16 + off, gy * 8
            c.rect(x + 1, y + 1, 14, 6, c.r.choice([13, 15, 40, 44]))
            c.rect(x + 1, y + 1, 14, 1, 16)
    c.noise([2, 13, 15, 36, 40, 48], 240)
    return c


def write_png(path, c):
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)  # filter: none
        raw.extend(c.p[y * SIZE:(y + 1) * SIZE])

    def chunk(t, data):
        return (struct.pack('>I', len(data)) + t + data +
                struct.pack('>I', zlib.crc32(t + data) & 0xffffffff))

    ihdr = struct.pack('>IIBBBBB', SIZE, SIZE, 8, 3, 0, 0, 0)
    plte = bytearray()
    for r, g, b in PAL[:64]:
        plte += bytes((r, g, b))
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) +
           chunk(b'PLTE', bytes(plte)) +
           chunk(b'IDAT', zlib.compress(bytes(raw), 9)) +
           chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)


TEXTURES = [
    ('beton.png', tex_beton), ('kraska.png', tex_kraska),
    ('plitka.png', tex_plitka), ('germodver.png', tex_germodver),
    ('dver.png', tex_dver), ('okno.png', tex_okno),
    ('poster.png', tex_poster), ('truby.png', tex_truby),
    ('treshchina.png', tex_treshchina), ('lift.png', tex_lift),
    ('pol.png', tex_pol), ('potolok.png', tex_potolok),
    ('sliz.png', tex_sliz), ('reshetka.png', tex_reshetka),
    ('klet.png', tex_klet), ('kirpich.png', tex_kirpich),
]

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in TEXTURES:
        write_png(os.path.join(OUT, name), fn())
        print('  res/textures/' + name)
    print('done:', len(TEXTURES), 'textures')
