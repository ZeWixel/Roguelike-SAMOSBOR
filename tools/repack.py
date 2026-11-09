#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Перепаковывает JAR после ручной правки графики. Ни JDK, ни javac, ни генераторы
не нужны: классы и manifest берутся из существующего JAR, а все файлы из
textures/, audio/ и gfx/ заменяются файлами с диска.

Варианты:

  1) Просто распаковать JAR, отредактировать PNG и запаковать обратно:

       mkdir myart && cd myart && unzip ../dist/GigaKhrushchyovka.jar
       # ... редактируем myart/gfx/weapon.png, myart/textures/beton.png ...
       cd .. && python3 tools/repack.py myart

  2) Правим сразу репозиторий (ресурсы лежат в res/):

       python3 tools/repack.py            # по умолчанию берёт res/

Можно добавлять НОВЫЕ файлы в этих каталогах (например gfx/screen-play.png) —
они появятся в JAR. Итоговый MIDlet-Jar-Size в .jad обновляется автоматически.
"""
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, 'dist', 'GigaKhrushchyovka.jar')
JAD = os.path.join(ROOT, 'dist', 'GigaKhrushchyovka.jad')
PREFIXES = ('textures/', 'audio/', 'gfx/')


def collect(srcdir):
    """-> {путь_в_JAR: путь_на_диске} для всех ресурсов под PREFIXES."""
    found = {}
    # Одинаково работает и для res/, и для распакованного JAR.
    mapping = [('textures/', 'textures/'), ('audio/', 'audio/'),
               ('gfx/', 'gfx/')]
    for jar_prefix, disk_prefix in mapping:
        base = os.path.join(srcdir, disk_prefix)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, files in os.walk(base):
            for name in sorted(files):
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, srcdir).replace(os.sep, '/')
                if rel.startswith(jar_prefix):
                    found[rel] = full
    return found


def main():
    if not os.path.isfile(JAR):
        sys.exit('Нет %s — сначала соберите игру: ./build.sh' % JAR)
    srcdir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'res')
    srcdir = os.path.abspath(srcdir)
    if not os.path.isdir(srcdir):
        sys.exit('Нет каталога с ресурсами: %s' % srcdir)

    assets = collect(srcdir)
    if not assets:
        sys.exit('В %s не нашлось textures/, audio/ или gfx/' % srcdir)

    with zipfile.ZipFile(JAR, 'r') as zin:
        old = zin.infolist()
        tmp = JAR + '.tmp'
        replaced, added = 0, 0
        with zipfile.ZipFile(tmp, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
            for info in old:
                name = info.filename
                data = zin.read(name)
                if name.startswith(PREFIXES) and name in assets:
                    with open(assets[name], 'rb') as f:
                        data = f.read()
                    replaced += 1
                # Сохраняем исходный ZipInfo (имена, порядок, атрибуты).
                zi = zipfile.ZipInfo(name, date_time=info.date_time)
                zi.compress_type = info.compress_type
                zi.external_attr = info.external_attr
                zi.internal_attr = info.internal_attr
                zi.create_system = info.create_system
                zi.comment = info.comment
                zout.writestr(zi, data)
            existing = set(i.filename for i in old)
            for name in sorted(assets):
                if name in existing or not name.startswith(PREFIXES):
                    continue
                with open(assets[name], 'rb') as f:
                    data = f.read()
                zout.writestr(name, data)
                added += 1
    os.replace(tmp, JAR)

    size = os.path.getsize(JAR)
    if os.path.isfile(JAD):
        with open(JAD, 'r', encoding='utf-8') as f:
            text = f.read()
        text = re.sub(r'(?m)^MIDlet-Jar-Size:.*$',
                      'MIDlet-Jar-Size: %d' % size, text)
        with open(JAD, 'w', encoding='utf-8') as f:
            f.write(text)

    print('JAR: %s' % JAR)
    print('  заменено ресурсов: %d, добавлено новых: %d' % (replaced, added))
    print('  MIDlet-Jar-Size: %d' % size)
    print('Готово: положите .jar и .jad рядом на телефон.')


if __name__ == '__main__':
    main()
