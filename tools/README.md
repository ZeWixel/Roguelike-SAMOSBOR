# Инструменты сборки

Из репозитория https://github.com/klaxons1/LLM-J2ME-SUITE (Agents.md приложен рядом):

* `cldc11.jar`, `midp21.jar` — UEI (API для компиляции `-bootclasspath`).
* `proguard.jar` — ProGuard 6.2.2: ключ `-microedition` добавляет в class-файлы
  атрибут `StackMap`, без которого JAR не стартует на реальном KVM/phoneme.

JDK 8 (javac с `-source 1.3 -target 1.3`) скачивается `build.sh` из
https://github.com/klaxons1/tools_linux_jdk8 в `~/.cache/jdk8` и в репозиторий не кладётся.
