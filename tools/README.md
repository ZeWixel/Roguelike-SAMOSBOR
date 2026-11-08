# Инструменты сборки

Из репозитория https://github.com/klaxons1/LLM-J2ME-SUITE (Agents.md приложен рядом):

* `cldc11.jar`, `midp21.jar` — UEI (API для компиляции `-bootclasspath`).
* `jsr135.jar` — MMAPI 1.2/JSR-135 (`Manager`, `Player`, `MIDIControl`,
  `VolumeControl`): нужен для MIDI-музыки и звуков `src/samosbor/Audio.java`.
  На телефоне без MMAPI игра ловит `NoClassDefFoundError` и работает без звука.
* `proguard.jar` — ProGuard 6.2.2: ключ `-microedition` добавляет в class-файлы
  атрибут `StackMap`, без которого JAR не стартует на реальном KVM/phoneme.

Генераторы ресурсов (обычный Python 3, в JAR не попадают). Запускаются **только**
при первом клоне или по `./build.sh --regen-art`: обычная сборка не перезаписывает
`res/`, поэтому ручные правки PNG/MIDI сохраняются.

* `gen_textures.py` — 16 **фотореалистичных** индексированных PNG 64×64 для
  стен/пола. Берёт компактные фото-основы из `photo_src/base_*.png`, смешивает с
  процедурной разметкой и квантует в 256-цветную `PLTE` с дизерингом
  Флойда–Стейнберга (PNG color type 3 / 8 bit — совместимо с MIDP).
* `photo_src/prepare.py` — подготовка `base_*.png` из больших ИИ-исходников, а
  также `res/textures/skin0..3.png` (макро-кожа) и `res/textures/beast0..3.png`
  (четыре цветовые морфы фото твари) для спрайтов. Промпты — в
  `photo_src/README.md`; большие исходники в репозиторий не кладутся.
* `gen_font.py` — кириллический bitmap-шрифт 5×7 (96 глифов). Обновляет только
  константы `GLYPHS`/`DATA` в `BitmapFont.java`, не затирая написанный вручную
  загрузчик открытого атласа `gfx/font.png`.
* `gen_resources_md.py` — пересобирает `RESOURCES.md` из реального `res/`.
* `gen_audio.py` — 14 SMF/MIDI: `theme.mid`, `danger.mid` (сирена) и 12 эффектов
  (включая `breach.mid` — пробой стены тварью).

`tools/preview/` — JDK8-превью и `SelfTest`: MIDP-, MMAPI- и RMS-заглушки, чтобы
тот же игровой Java-код запускался на десктопе. В J2ME-JAR не включается.

JDK 8 (javac с `-source 1.3 -target 1.3`) скачивается `build.sh` из
https://github.com/klaxons1/tools_linux_jdk8 в `~/.cache/jdk8` и в репозиторий не кладётся.


Открытые ресурсы (графика редактируется без пересборки):

* `preview/ExportGfx.java` — один раз экспортирует всю процедурную графику
  (оружие, 6 предметов, 3 титульных твари, атлас шрифта, прицел, HUD, 6 фонов,
  оверлей, палитру) в `res/gfx/*.png` теми же методами, которыми игра её рисовала.
  Дальше PNG — источник, а Java-код остаётся фолбэком.
* `repack.py` — берёт классы и manifest из готового JAR, заменяет `textures/`,
  `audio/`, `gfx/`, добавляет новые файлы и обновляет `MIDlet-Jar-Size` в JAD.
  Ни JDK, ни javac, ни ProGuard не нужны.
* `RESOURCES.md` в корне репозитория — полный манифест всех файлов JAR.
