#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Генератор MIDI (Standard MIDI File, format 0) для «Гигахрущёвки».
Музыка + звуки пишутся в res/audio/*.mid и проигрываются через JSR-135 MMAPI.
Никаких внешних зависимостей: SMF собирается байтами.
"""
import os
import struct

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   'res', 'audio')
DIV = 480  # ticks на четверть


def vlq(n):
    out = [n & 0x7f]
    n >>= 7
    while n:
        out.append((n & 0x7f) | 0x80)
        n >>= 7
    out.reverse()
    return bytes(out)


class Track:
    def __init__(self):
        self.events = []  # (tick, bytes)

    def add(self, tick, data):
        self.events.append((tick, bytes(data)))

    def tempo(self, us):
        self.add(0, [0xff, 0x51, 0x03, (us >> 16) & 255, (us >> 8) & 255, us & 255])

    def program(self, ch, prog, tick=0):
        self.add(tick, [0xc0 | ch, prog])

    def note(self, ch, pitch, start, length, vel=90):
        self.add(start, [0x90 | ch, pitch, vel])
        self.add(start + length, [0x80 | ch, pitch, 0])

    def ctrl(self, ch, num, val, tick=0):
        self.add(tick, [0xb0 | ch, num, val])

    def bytes(self):
        self.events.sort(key=lambda e: e[0])
        raw = bytearray()
        last = 0
        for tick, data in self.events:
            raw += vlq(tick - last)
            raw += data
            last = tick
        raw += vlq(0) + bytes([0xff, 0x2f, 0x00])  # end of track
        return (b'MTrk' + struct.pack('>I', len(raw)) + bytes(raw))


def write_midi(name, track):
    hdr = b'MThd' + struct.pack('>IHHH', 6, 0, 1, DIV)
    path = os.path.join(OUT, name)
    with open(path, 'wb') as f:
        f.write(hdr + track.bytes())
    print('  res/audio/' + name)


# ---------------------------------------------------------------------- музыка

def theme():
    """Медленный хоррор-луп: гул, минорное арпеджио, сиренный мотив."""
    t = Track()
    t.tempo(620000)          # ~97 BPM
    t.program(0, 48)         # String Ensemble 1 — гул Гигахрущёвки
    t.program(1, 80)         # Lead 1 (square) — мелодия
    t.program(2, 0)          # Piano — капли
    t.ctrl(0, 7, 95)
    t.ctrl(1, 7, 70)
    t.ctrl(2, 7, 55)

    bar = DIV * 4
    # гудящий органный пункт A1 на весь цикл (8 тактов)
    for b in range(8):
        t.note(0, 33, b * bar, bar - 10, 70)
        t.note(0, 40, b * bar, bar - 10, 45)   # E2

    # минорное арпеджио A-C-E-A, со сдвигом в F и G#
    seq = [57, 60, 64, 69, 65, 60, 57, 56, 53, 57, 60, 64, 68, 64, 60, 57]
    for i, p in enumerate(seq):
        tick = i * (DIV // 2)
        t.note(1, p, tick, DIV // 2 - 30, 62 + (i % 3) * 8)

    # редкие «капли» и сиренный всхлип в конце цикла
    for i, p in enumerate([81, 79, 76, 72, 76, 79]):
        t.note(2, p, (i * DIV) + DIV * 10, DIV // 3, 48)
    t.note(1, 68, bar * 7, DIV * 2, 78)
    t.note(1, 69, bar * 7 + DIV * 2, DIV * 2, 84)
    return t


def danger():
    """Последние 30 секунд: сирена, ускоренный пульс."""
    t = Track()
    t.tempo(300000)          # 200 BPM
    t.program(0, 80)
    t.program(1, 48)
    t.ctrl(0, 7, 100)
    t.ctrl(1, 7, 60)
    for i in range(16):
        tick = i * DIV
        t.note(0, 69 if i % 2 == 0 else 68, tick, DIV - 20, 105)
        t.note(1, 45, tick, DIV - 10, 60)
    return t


# ----------------------------------------------------------------------- звуки

def sfx(name, notes, tempo=500000, program=80, perc=False):
    t = Track()
    t.tempo(tempo)
    t.program(0, program)
    t.ctrl(0, 7, 110)
    for ch, pitch, start, length, vel in notes:
        t.add(start, [0x90 | ch, pitch, vel])
        t.add(start + length, [0x80 | ch, pitch, 0])
    return name, t


SOUNDS = [
    # выстрел Ералашникова: щелчок + низкий хлопок
    sfx('shot.mid', [(9, 38, 0, 60, 120), (0, 36, 0, 110, 110),
                     (0, 31, 20, 100, 80)], 400000, 80),
    # попадание в тварь
    sfx('hit.mid', [(0, 76, 0, 90, 110), (0, 70, 70, 110, 90)], 400000, 80),
    # тварь разобрана на части
    sfx('kill.mid', [(0, 72, 0, 80, 100), (0, 67, 90, 80, 100),
                     (0, 64, 180, 80, 100), (0, 57, 270, 200, 110)], 450000, 48),
    # подбор предмета
    sfx('pickup.mid', [(0, 64, 0, 70, 90), (0, 69, 80, 70, 95),
                       (0, 76, 160, 140, 105)], 450000, 0),
    # гермодверь
    sfx('door.mid', [(0, 45, 0, 160, 100), (0, 43, 120, 160, 100),
                     (0, 40, 240, 260, 105)], 500000, 48),
    # урон
    sfx('hurt.mid', [(0, 58, 0, 180, 110), (0, 59, 0, 180, 110),
                     (0, 46, 120, 220, 100)], 400000, 80),
    # предупреждение сирены
    sfx('siren.mid', [(0, 69, 0, 220, 105), (0, 68, 240, 220, 105),
                      (0, 69, 480, 220, 105), (0, 68, 720, 220, 105)], 400000, 80),
    # пункт меню / перелистывание
    sfx('menu.mid', [(0, 72, 0, 60, 85)], 500000, 0),
    # шаг по клетке
    sfx('step.mid', [(9, 36, 0, 30, 55)], 500000, 0),
    # достать / убрать оружие
    sfx('draw.mid', [(9, 31, 0, 40, 70), (0, 55, 50, 90, 75)], 450000, 80),
    # перезарядка
    sfx('reload.mid', [(9, 31, 0, 40, 70), (9, 31, 130, 40, 70),
                       (0, 60, 260, 80, 80)], 450000, 80),
]


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    write_midi('theme.mid', theme())
    write_midi('danger.mid', danger())
    for name, track in SOUNDS:
        write_midi(name, track)
    print('done:', 2 + len(SOUNDS), 'MIDI files')
