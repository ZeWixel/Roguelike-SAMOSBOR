#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Пересобирает RESOURCES.md из реального содержимого res/.

Запускать после добавления/переименования ресурсов:
    python3 tools/gen_resources_md.py
"""
import os
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'res'


def png_size(path):
    d = path.read_bytes()
    if d[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    w, h, _bd, _ct = struct.unpack('>IIBB', d[16:26])
    return w, h


rows = []
for f in sorted(RES.rglob('*')):
    if not f.is_file():
        continue
    jar = f.relative_to(RES).as_posix()
    if f.suffix == '.png':
        dim = png_size(f)
        size = '%dx%d' % dim if dim else '?'
    elif f.suffix == '.mid':
        size = 'MIDI'
    elif f.suffix == '.txt':
        size = 'TXT'
    else:
        size = '-'
    rows.append((jar, size, f.stat().st_size))

GROUPS = [
    ('Стены, пол, потолок (`textures/*.png`, 64x64, индексированные PNG)',
     lambda j: j.startswith('textures/') and not any(k in j for k in ('skin', 'beast'))),
    ('Фото-основы спрайтов (`textures/skin*`, `textures/beast*`, 40x56)',
     lambda j: j.startswith('textures/') and any(k in j for k in ('skin', 'beast'))),
    ('Спрайты и интерфейс (`gfx/*.png`)',
     lambda j: j.startswith('gfx/') and '/source/' not in j and j.endswith('.png')),
    ('Исходные фото-основы (`gfx/source/*.png`)',
     lambda j: j.startswith('gfx/source/')),
    ('Палитра и документация (`gfx/*.txt`)',
     lambda j: j.startswith('gfx/') and j.endswith('.txt')),
    ('Музыка и эффекты (`audio/*.mid`)',
     lambda j: j.startswith('audio/')),
]

ITEMS = ['пропуск Партии', 'белый шар (карта)', 'чёрный шар (предметы)',
         'патроны', 'пищконцентрат', '+20 секунд']
PURPOSE = {
    'gfx/weapon.png': 'FP-оружие «Ералашников» в режиме прицеливания',
    'gfx/font.png': 'атлас шрифта 5x7: 16 колонок, ячейка 6x8, непрозрачный пиксель = точка глифа',
    'gfx/crosshair.png': 'маска прицела (цвет берётся из `CROSSHAIR` в палитре)',
    'gfx/crosshair-hot.png': 'маска прицела, когда он наведён на тварь (`CROSSHAIR_HOT`)',
    'gfx/hud.png': 'нижняя панель HUD, поверх неё рисуется текст',
    'gfx/overlay.png': 'слой поверх каждого кадра (виньетка, CRT-маска); по умолчанию прозрачный',
    'gfx/theme.txt': 'все цвета UI, панелей, карты, прицела и тумана',
    'gfx/README.txt': 'краткая памятка внутри самого JAR',
}
for i, label in enumerate(ITEMS, 1):
    PURPOSE['gfx/item-%d.png' % i] = 'спрайт предмета: %s' % label
for i in range(3):
    PURPOSE['gfx/title-%d.png' % i] = 'тварь №%d на титульном экране и в концовках' % (i + 1)
SCREEN_NAMES = {
    'title': 'главное меню', 'help': 'управление', 'brief': 'брифинг этажа',
    'ending': 'концовка', 'dead': 'гибель', 'progress': 'карта продвижения',
}


def purpose(jar):
    if jar in PURPOSE:
        return PURPOSE[jar]
    if jar.startswith('textures/') and not any(k in jar for k in ('skin', 'beast')):
        return 'текстура стены/пола/потолка'
    if jar.startswith('textures/'):
        return 'фото-фактура для спрайтов тварей'
    if jar.startswith('gfx/source/'):
        return 'исходная фотография для `tools/gen_textures.py`'
    if jar.startswith('audio/'):
        return 'MIDI: музыка или звуковой эффект'
    if jar.startswith('gfx/screen-'):
        key = os.path.basename(jar)[len('screen-'):-4]
        return 'полноэкранный фон: %s' % SCREEN_NAMES.get(key, key)
    return ''


out = []
out.append('# Открытые ресурсы «Гигахрущёвки»\n')
out.append('Вся графика и звук игры лежат **внутри `dist/GigaKhrushchyovka.jar` обычными файлами**.\n'
           'Никаких скрытых атласов или спрайтов, зашитых в байт-код: всё, что видит игра,\n'
           'лежит отдельными файлами. Разархивировал JAR, отредактировал PNG/TXT в любом\n'
           'редакторе, запаковал обратно — игра подхватит изменения без `javac`, ProGuard\n'
           'и пересборки.\n')
out.append('\n## Как редактировать\n')
out.append('```sh\n'
           '# 1. распаковать готовый JAR\n'
           'mkdir myart && cd myart && unzip ../dist/GigaKhrushchyovka.jar\n\n'
           '# 2. отредактировать что угодно, например:\n'
           '#    myart/gfx/weapon.png        - оружие от первого лица\n'
           '#    myart/textures/beton.png   - стены\n'
           '#    myart/gfx/theme.txt        - цвета интерфейса и тумана\n'
           '#    myart/audio/theme.mid      - музыка\n\n'
           '# 3. запаковать обратно (обновит JAR и MIDlet-Jar-Size в JAD)\n'
           'cd .. && python3 tools/repack.py myart\n'
           '```\n')
out.append('Альтернатива: править файлы прямо в `res/` репозитория и вызвать\n'
           '`python3 tools/repack.py` (по умолчанию читает `res/`). Обычный\n'
           '`./build.sh` **не перезаписывает** `res/`; генераторы и экспорт спрайтов\n'
           'запускаются только при первом клоне или по `./build.sh --regen-art`.\n')
out.append('\nПравила:\n\n'
           '* сохраняйте **размер в пикселях** у каждого файла (список ниже);\n'
           '* `alpha = 0` — прозрачный пиксель, `alpha = 255` — непрозрачный;\n'
           '* `gfx/theme.txt` — строки `KEY=0xAARRGGBB`, `#` — комментарий,\n'
           '  допустим и короткий вид `0xRRGGBB` (альфа `FF` добавляется сама);\n'
           '* если удалить PNG, игра нарисует соответствующий спрайт старым\n'
           '  процедурным кодом (фолбэк), а не упадёт;\n'
           '* новые файлы в `gfx/` тоже попадут в JAR при перепаковке — например,\n'
           '  `gfx/screen-play.png` станет фоном карты этажа.\n')
out.append('\n## Что за что отвечает\n')
for title, pred in GROUPS:
    sel = [(j, sz, n) for j, sz, n in rows if pred(j)]
    if not sel:
        continue
    out.append('\n### %s\n\n' % title)
    out.append('| Файл | Размер | Байт | Что меняет |\n|---|---:|---:|---|\n')
    for jar, size, n in sel:
        out.append('| `%s` | %s | %d | %s |\n' % (jar, size, n, purpose(jar)))
plural = 'файл' if len(rows) % 10 == 1 and len(rows) % 100 != 11 else 'файла'
if len(rows) % 10 in (2, 3, 4) and len(rows) % 100 not in (12, 13, 14):
    plural = 'файла'
elif len(rows) % 10 != 1 or len(rows) % 100 == 11:
    plural = 'файлов'
out.append('\nИтого: **%d %s** в `res/`, все попадают в JAR. Плюс class-файлы\n'
           '`samosbor/*.class` и `META-INF/MANIFEST.MF` (код, не ресурсы).\n'
           % (len(rows), plural))
out.append('\n`gfx/screen-play.png` и `gfx/screen-pause.png` **не** поставляются:\n'
           'добавьте их сами, если хотите заменить фон 3D-экрана/карты или паузы.\n'
           'Поддерживаются все 8 имён: `title`, `help`, `brief`, `play`,\n'
           '`ending`, `dead`, `pause`, `progress`.\n')
out.append('\n## Технически\n')
out.append('`src/samosbor/Gfx.java` грузит PNG через `Image.createImage()` →\n'
           '`Texture.loadPixels()` (без лишнего массива `vb` для больших пластин),\n'
           'читает `theme.txt` потоком `Class.getResourceAsStream()` и кэширует\n'
           'результат. Фоновые пластины грузятся лениво, в памяти живёт только одна.\n'
           '`BitmapFont` при наличии `gfx/font.png` рисует из атласа, иначе из\n'
           'встроенной таблицы `DATA`. `Game.fill()` подкладывает фоновую пластину,\n'
           '`Gfx.drawOverlay()` — верхний слой. Всё целочисленное, CLDC 1.1/MIDP 2.0,\n'
           'без `float`, без аллокаций в кадре. Проверяется `SelfTest.testOpenResources()`.\n'
           'Манифест пересобирается: `python3 tools/gen_resources_md.py`.\n')

(ROOT / 'RESOURCES.md').write_text(''.join(out))
print('RESOURCES.md: %d files' % len(rows))
