#!/bin/sh
# Сборка «Гигахрущёвки» для J2ME (CLDC 1.1 / MIDP 2.0, java 1.3).
# Использует UEI и ProGuard из LLM-J2ME-SUITE (см. tools/README.md).
#
#   ./build.sh               — обычная сборка; НЕ перезаписывает графику в res/
#   ./build.sh --regen-art   — заново сгенерировать PNG/MIDI/шрифт/атлас
#
# Все ресурсы открыты: они лежат в res/ и копируются в JAR как есть.
# После ручной правки PNG достаточно python3 tools/repack.py — без javac.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"

REGEN=0
for arg in "$@"; do
    case "$arg" in
        --regen-art) REGEN=1 ;;
        *) echo "Неизвестный параметр: $arg" >&2; exit 2 ;;
    esac
done

JDK8=${JDK8:-"$HOME/.cache/jdk8"}
if [ ! -x "$JDK8/bin/javac" ]; then
    echo "==> Скачиваю JDK 8 (klaxons1/tools_linux_jdk8) в $JDK8"
    mkdir -p "$JDK8"
    curl -sL --retry 2 \
        https://codeload.github.com/klaxons1/tools_linux_jdk8/tar.gz/refs/heads/master \
        | tar -xz -C "$JDK8" --strip-components=1
    chmod +x "$JDK8/bin/"* "$JDK8/jre/bin/"* 2>/dev/null || true
fi
JAVAC="$JDK8/bin/javac"
JAVA="$JDK8/bin/java"
JAR="$JDK8/bin/jar"
JAVAP="$JDK8/bin/javap"

BUILD="$ROOT/build"
DIST="$ROOT/dist"
CLDC="$ROOT/tools/cldc11.jar"
MIDP="$ROOT/tools/midp21.jar"
MMAPI="$ROOT/tools/jsr135.jar"
PROGUARD="$ROOT/tools/proguard.jar"

rm -rf "$BUILD/classes" "$BUILD/preverified" "$BUILD/preview" "$BUILD/screenshots"
mkdir -p "$BUILD/classes" "$BUILD/preverified" "$BUILD/preview" "$BUILD/screenshots" "$DIST"

# Генераторы нужны ТОЛЬКО при первом клоне или по явному --regen-art.
# Иначе ручные правки res/*.png переживают любую пересборку.
if [ "$REGEN" = 1 ] || [ ! -f src/samosbor/BitmapFont.java ] \
        || [ ! -f res/textures/beton.png ] || [ ! -f res/audio/menu.mid ]; then
    echo "==> Генераторы исходных ресурсов (первый запуск / --regen-art)"
    python3 tools/gen_textures.py >/dev/null
    python3 tools/gen_font.py >/dev/null
    python3 tools/gen_audio.py >/dev/null
else
    echo "==> Ресурсы из res/ не трогаем (правки сохраняются)"
fi

echo "==> javac -source 1.3 -target 1.3"
find src -name '*.java' | sort > "$BUILD/sources.txt"
"$JAVAC" -source 1.3 -target 1.3 -encoding UTF-8 -Xlint:-options \
    -bootclasspath "$CLDC:$MIDP:$MMAPI" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "==> ProGuard -microedition (StackMap для KVM/phoneme)"
cat > "$BUILD/proguard.cfg" <<EOF
-injars      '$BUILD/classes'
-outjars     '$BUILD/preverified'
-libraryjars '$CLDC'
-libraryjars '$MIDP'
-libraryjars '$MMAPI'
-dontshrink
-dontoptimize
-dontobfuscate
-dontwarn
-microedition
-keep public class samosbor.Main
EOF
"$JAVA" -jar "$PROGUARD" @"$BUILD/proguard.cfg" > "$BUILD/proguard.log" 2>&1 || {
    cat "$BUILD/proguard.log"; exit 1;
}
if ! "$JAVAP" -v -classpath "$BUILD/preverified" samosbor.Main 2>/dev/null | grep -q StackMap; then
    echo "ВНИМАНИЕ: атрибут StackMap не найден — проверьте $BUILD/proguard.log"
else
    echo "    StackMap: ok"
fi

echo "==> Превью-классы (MIDP/MMAPI/RMS-заглушки)"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
    -d "$BUILD/preview" \
    $(find tools/preview/stub -name '*.java' | sort) \
    $(find src -name '*.java' | sort) \
    tools/preview/Preview.java tools/preview/SelfTest.java \
    tools/preview/ExportGfx.java

# Экспорт процедурных спрайтов в PNG делается один раз; дальше PNG — источник.
if [ "$REGEN" = 1 ] || [ ! -f res/gfx/weapon.png ] || [ ! -f res/gfx/theme.txt ]; then
    echo "==> Экспорт всей графики в res/gfx/ (открытые ресурсы)"
    "$JAVA" -cp "$BUILD/preview:$ROOT/res" samosbor.ExportGfx "$ROOT/res/gfx"
else
    echo "==> res/gfx/ уже есть, используем отредактированные PNG"
fi

echo "==> Ресурсы"
mkdir -p "$BUILD/preverified/textures" "$BUILD/preverified/audio" \
         "$BUILD/preverified/gfx/source"
cp res/textures/*.png "$BUILD/preverified/textures/"
cp res/audio/*.mid "$BUILD/preverified/audio/"
# Только файлы, не каталоги: PNG/MIDI/TXT лежат в gfx/ и gfx/source/.
find res/gfx -type f ! -name '*.tmp' -print | while read -r f; do
    rel=${f#res/gfx/}
    mkdir -p "$BUILD/preverified/gfx/$(dirname "$rel")"
    cp "$f" "$BUILD/preverified/gfx/$rel"
done
# gfx/source/*.png — исходные фото-основы текстур, тоже лежат в JAR.

cat > "$BUILD/MANIFEST.MF" <<'EOF'
Manifest-Version: 1.0
Created-By: GigaKhrushchyovka build.sh (J2ME)
MIDlet-1: GigaKhrushchyovka,,samosbor.Main
MIDlet-Name: GigaKhrushchyovka: SAMOSBOR
MIDlet-Vendor: SAMOSBOR / Arena
MIDlet-Version: 1.6.0
MIDlet-Description: Roguelike v pervom litse po vselennoi Samosbor
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0

EOF

echo "==> JAR / JAD"
"$JAR" cfm "$DIST/GigaKhrushchyovka.jar" "$BUILD/MANIFEST.MF" \
    -C "$BUILD/preverified" .
SIZE=$(wc -c < "$DIST/GigaKhrushchyovka.jar" | tr -d ' ')
cat > "$DIST/GigaKhrushchyovka.jad" <<EOF
MIDlet-1: GigaKhrushchyovka,,samosbor.Main
MIDlet-Jar-Size: $SIZE
MIDlet-Jar-URL: GigaKhrushchyovka.jar
MIDlet-Name: GigaKhrushchyovka: SAMOSBOR
MIDlet-Vendor: SAMOSBOR / Arena
MIDlet-Version: 1.6.0
MIDlet-Description: Roguelike v pervom litse po vselennoi Samosbor
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF

echo "==> Самотест и кадры превью (тот же Java-код на заглушках)"
"$JAVA" -cp "$BUILD/preview:$ROOT/res" samosbor.SelfTest
"$JAVA" -cp "$BUILD/preview:$ROOT/res" samosbor.Preview --capture "$BUILD/screenshots"

echo "==> Готово"
ls -lh "$DIST/GigaKhrushchyovka.jar" "$DIST/GigaKhrushchyovka.jad"
echo "    dist/GigaKhrushchyovka.jar  — ставить на телефон вместе с .jad"
echo "    build/screenshots/          — кадры из превью"
echo "    python3 tools/repack.py     — перепаковать JAR после правки PNG"
