#!/bin/sh
# Builds dist/Spellblade.jar: compiled for Java 17, with the drawn art and the recorded music inside, runnable with a double-click or
# `java -jar dist/Spellblade.jar`. Needs a JDK (17+).
cd "$(dirname "$0")" || exit 1
rm -rf out_dist && mkdir out_dist || exit 1
javac --release 17 -d out_dist src/game/*.java || { rm -rf out_dist; exit 1; }
mkdir -p dist
printf 'Main-Class: game.Main\n' > out_dist/MANIFEST.MF
jar cfm dist/Spellblade.jar out_dist/MANIFEST.MF -C out_dist game -C res art -C res music || { rm -rf out_dist; exit 1; }
rm -rf out_dist
echo "Built dist/Spellblade.jar"
