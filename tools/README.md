# Инструменты сборки

Из репозитория https://github.com/klaxons1/LLM-J2ME-SUITE (Agents.md приложен рядом):

* `cldc11.jar`, `midp21.jar` — UEI (API для компиляции `-bootclasspath`).
* `jsr135.jar` — MMAPI 1.2/JSR-135 (`Manager`, `Player`, `MIDIControl`,
  `VolumeControl`): нужен для MIDI-музыки и звуков `src/samosbor/Audio.java`.
  На телефоне без MMAPI игра ловит `NoClassDefFoundError` и работает без звука.
* `proguard.jar` — ProGuard 6.2.2: ключ `-microedition` добавляет в class-файлы
  атрибут `StackMap`, без которого JAR не стартует на реальном KVM/phoneme.

Генераторы ресурсов (обычный Python 3, в JAR не попадают):

* `gen_textures.py` — 16 индексированных PNG 64×64 для стен/пола.
* `gen_font.py` — кириллический bitmap-шрифт 5×7 (96 глифов).
* `gen_audio.py` — 14 SMF/MIDI: `theme.mid`, `danger.mid` (сирена) и 12 эффектов
  (включая `breach.mid` — пробой стены тварью).

`tools/preview/` — JDK8-превью и `SelfTest`: MIDP-, MMAPI- и RMS-заглушки, чтобы
тот же игровой Java-код запускался на десктопе. В J2ME-JAR не включается.

JDK 8 (javac с `-source 1.3 -target 1.3`) скачивается `build.sh` из
https://github.com/klaxons1/tools_linux_jdk8 в `~/.cache/jdk8` и в репозиторий не кладётся.
