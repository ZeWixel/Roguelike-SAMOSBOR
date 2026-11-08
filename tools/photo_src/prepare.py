#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Одноразовая подготовка фото-исходников (сгенерированы ИИ, ~1408x768 RGB):
  * инструменты/photo_src/base_*.png — 64x64 бесшовные основы для gen_textures.py;
  * res/textures/skin0..3.png        — 40x56 органические текстуры для спрайтов.

Большие исходные PNG в репозиторий не кладутся (компактные основы уже
содержат всю нужную информацию). Промпты см. в README.md этого каталога.

Запуск: python3 tools/photo_src/prepare.py
"""
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
TEX = os.path.join(ROOT, 'res', 'textures')

MATERIALS = ['concrete', 'tile', 'metal', 'cracked', 'slime']
SKINS = ['flesh', 'violet', 'rustflesh', 'pale']
BEAST = 'beast0.png'
# Лимит ИИ-генерации за один ход = 10 кадров; один полноценный фото-снимок
# твари даёт четыре цветовые морфы для четырёх палитр ГПСЧ.
BEAST_TINTS = [
    (1.00, 1.00, 1.00),  # серо-зелёный
    (1.05, 0.55, 1.30),  # фиолетовая слизь
    (1.40, 0.88, 0.52),  # ржавчина
    (1.12, 1.14, 1.20),  # бледная нелюдь
]


def read_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n', path
    pos, idat, w, h, ct, bd = 8, bytearray(), 0, 0, 0, 0
    while pos + 8 <= len(d):
        ln = struct.unpack('>I', d[pos:pos + 4])[0]
        typ = d[pos + 4:pos + 8]
        chunk = d[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', chunk[:10])
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
    assert bd == 8 and ct in (2, 6), (path, bd, ct)
    bpp = 3 if ct == 2 else 4
    raw = zlib.decompress(bytes(idat))
    stride = w * bpp
    prev = bytearray(stride)
    rows = []
    p = 0
    for _ in range(h):
        ft = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        rows.append(line)
        prev = line
    px = []
    for line in rows:
        for i in range(w):
            o = i * bpp
            px.append((line[o], line[o + 1], line[o + 2]))
    return w, h, px


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b = px[y * w + x]
            raw += bytes((max(0, min(255, r)), max(0, min(255, g)),
                          max(0, min(255, b))))

    def chunk(t, data):
        return (struct.pack('>I', len(data)) + t + data +
                struct.pack('>I', zlib.crc32(t + data) & 0xffffffff))

    ihdr = struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) +
           chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)


def resample(src_w, src_h, src, out_w, out_h):
    """Квадратный центр -> блоковое усреднение (бокс-фильтр)."""
    side = min(src_w, src_h)
    ox = (src_w - side) // 2
    oy = (src_h - side) // 2
    out = []
    for y in range(out_h):
        y0 = oy + side * y // out_h
        y1 = max(y0 + 1, oy + side * (y + 1) // out_h)
        for x in range(out_w):
            x0 = ox + side * x // out_w
            x1 = max(x0 + 1, ox + side * (x + 1) // out_w)
            r = g = b = n = 0
            for sy in range(y0, y1, max(1, (y1 - y0) // 6)):
                for sx in range(x0, x1, max(1, (x1 - x0) // 6)):
                    c = src[sy * src_w + sx]
                    r += c[0]; g += c[1]; b += c[2]; n += 1
            out.append((r // n, g // n, b // n))
    return out


def resample_portrait(src_w, src_h, src, out_w, out_h):
    """Центральная вертикальная полоса под пропорцию спрайта 40x56."""
    cw = int(src_h * out_w / float(out_h))
    ox = (src_w - cw) // 2
    out = []
    for y in range(out_h):
        y0 = src_h * y // out_h
        y1 = max(y0 + 1, src_h * (y + 1) // out_h)
        for x in range(out_w):
            x0 = ox + cw * x // out_w
            x1 = max(x0 + 1, ox + cw * (x + 1) // out_w)
            r = g = b = n = 0
            for sy in range(y0, y1, max(1, (y1 - y0) // 6)):
                for sx in range(x0, x1, max(1, (x1 - x0) // 6)):
                    c = src[sy * src_w + sx]
                    r += c[0]; g += c[1]; b += c[2]; n += 1
            out.append((r // n, g // n, b // n))
    return out


def tint(px, mul):
    return [tuple(min(255, int(c[k] * mul[k])) for k in range(3)) for c in px]


def make_tileable(px, w, h, border=7):
    """Сглаживает швы: края смешиваются с зеркальным отражением."""
    out = list(px)
    for y in range(h):
        for x in range(w):
            wx = min(x, w - 1 - x)
            wy = min(y, h - 1 - y)
            d = min(wx, wy)
            if d >= border:
                continue
            t = (border - d) / float(border)
            a = out[y * w + x]
            b = px[(h - 1 - y) * w + (w - 1 - x)]
            out[y * w + x] = tuple(int(a[k] * (1 - t * 0.5) + b[k] * t * 0.5)
                                   for k in range(3))
    return out


def main():
    os.makedirs(TEX, exist_ok=True)
    for name in MATERIALS + SKINS:
        big = os.path.join(HERE, name + '.png')
        if not os.path.exists(big):
            print('  пропуск (нет исходника):', big)
            continue
        sw, sh, src = read_png(big)
        base = make_tileable(resample(sw, sh, src, 64, 64), 64, 64)
        write_png(os.path.join(HERE, 'base_' + name + '.png'), 64, 64, base)
        print('  base_%s.png 64x64  (из %dx%d)' % (name, sw, sh))
        if name in SKINS:
            i = SKINS.index(name)
            skin = make_tileable(resample(sw, sh, src, 40, 56), 40, 56, 4)
            write_png(os.path.join(TEX, 'skin%d.png' % i), 40, 56, skin)
            print('  res/textures/skin%d.png 40x56' % i)

    big = os.path.join(HERE, BEAST)
    small = os.path.join(HERE, 'base_beast.png')
    if os.path.exists(big):
        sw, sh, src = read_png(big)
        base = resample_portrait(sw, sh, src, 40, 56)
        write_png(small, 40, 56, base)
        print('  base_beast.png 40x56')
    elif os.path.exists(small):
        sw, sh, base = read_png(small)
    else:
        base = None
        print('  пропуск (нет исходника):', big)
    if base is not None:
        for i, mul in enumerate(BEAST_TINTS):
            write_png(os.path.join(TEX, 'beast%d.png' % i), 40, 56,
                      tint(base, mul))
            print('  res/textures/beast%d.png 40x56' % i)
    print('done')


if __name__ == '__main__':
    main()
