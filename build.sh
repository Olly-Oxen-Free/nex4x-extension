#!/bin/bash
set -e

PATCH_DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSES_DIR="$PATCH_DIR/jars/classes"
JAR_FILE="$PATCH_DIR/jars/Nex4xExpansion.jar"

STARSECTOR="$HOME/Games/Starsector"
API="$STARSECTOR/starfarer.api.jar"
LOG4J="$STARSECTOR/log4j-1.2.9.jar"
JSON="$STARSECTOR/json.jar"
NEX="$STARSECTOR/mods/Nexerelin-0.12.1d/jars/ExerelinCore.jar"
ASHLIB="$STARSECTOR/mods/Ashlib-2.1.2/jars/ashlib.jar"
LAZYLIB="$STARSECTOR/mods/LazyLib-3.0.0/jars/LazyLib.jar"
LWJGL="$STARSECTOR/lwjgl.jar"
CLASSPATH="$API:$LOG4J:$JSON:$NEX:$ASHLIB:$LAZYLIB:$LWJGL"

echo "=== Nexerelin 4X Expansion Build ==="

# Clean
echo "[1/3] Cleaning..."
rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

# Compile
echo "[2/3] Compiling..."
SOURCES=()
while IFS= read -r -d '' f; do
    SOURCES+=("$f")
done < <(find "$PATCH_DIR/src" -name "*.java" -print0)

javac -source 1.7 -target 1.7 -cp "$CLASSPATH" -d "$CLASSES_DIR" "${SOURCES[@]}" 2>&1

echo "   Compiled ${#SOURCES[@]} source files"

# Package
echo "[3/3] Creating JAR..."
cd "$CLASSES_DIR"
jar cf "$JAR_FILE" nex4x/

echo "BUILD SUCCESSFUL"
ls -lh "$JAR_FILE"
