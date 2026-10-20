#!/bin/bash

# Сборка БД Древо
# Использование:
#   ./make.sh patch   — создать патч к существующей mainBase
#   ./make.sh full    — пересоздать mainBase и patchBase
#   ./make.sh         — интерактивный выбор режима

set -eo pipefail

MODE=${1:-""}
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
DB_DIR="$ROOT_DIR/DB"

# --- Скачивание данных ---
echo "Скачивание данных..."
cd "$DB_DIR"
./download.sh
cd "$ROOT_DIR"

# --- Определяем режим ---
if [ -z "$MODE" ]; then
    echo "Выберите режим сборки:"
    echo "  1) patch — патч к существующей main (по умолчанию)"
    echo "  2) full  — пересоздать main и patch"
    read -p "Режим [1]: " CHOICE
    case "$CHOICE" in
        2|full)  MODE="full" ;;
        *)       MODE="patch" ;;
    esac
fi

if [ "$MODE" != "patch" ] && [ "$MODE" != "full" ]; then
    echo "Ошибка: неизвестный режим '$MODE'. Используйте 'patch' или 'full'."
    exit 1
fi

# --- Автоопределение версий ---
# Находим текущий номер main
CURRENT_MAIN=$(ls "$DB_DIR"/main.*.ru.drevoinfo.obb 2>/dev/null | sed 's/.*main\.\([0-9]*\)\..*/\1/' | sort -n | tail -1)
# Находим текущий номер patch
CURRENT_PATCH=$(ls "$DB_DIR"/patch.*.ru.drevoinfo.obb 2>/dev/null | sed 's/.*patch\.\([0-9]*\)\..*/\1/' | sort -n | tail -1)

if [ -z "$CURRENT_MAIN" ]; then
    echo "Ошибка: не найден файл main.*.ru.drevoinfo.obb в $DB_DIR"
    exit 1
fi
if [ -z "$CURRENT_PATCH" ]; then
    echo "Ошибка: не найден файл patch.*.ru.drevoinfo.obb в $DB_DIR"
    exit 1
fi

NEW_PATCH=$((CURRENT_PATCH + 1))

if [ "$MODE" = "patch" ]; then
    MAIN_VERSION=$CURRENT_MAIN
    PATCH_VERSION=$NEW_PATCH
    echo ""
    echo "=== Режим: PATCH ==="
    echo "Существующая main: main.${CURRENT_MAIN}.ru.drevoinfo.obb"
    echo "Текущий patch:     patch.${CURRENT_PATCH}.ru.drevoinfo.obb"
    echo "---"
    echo "Будет создан:      patch.${PATCH_VERSION}.ru.drevoinfo.obb (патч к main.${MAIN_VERSION})"
else
    MAIN_VERSION=$NEW_PATCH
    PATCH_VERSION=$NEW_PATCH
    echo ""
    echo "=== Режим: FULL ==="
    echo "Существующая main: main.${CURRENT_MAIN}.ru.drevoinfo.obb (будет заменена)"
    echo "Текущий patch:     patch.${CURRENT_PATCH}.ru.drevoinfo.obb"
    echo "---"
    echo "Будут созданы:     main.${MAIN_VERSION}.ru.drevoinfo.obb"
    echo "                   patch.${PATCH_VERSION}.ru.drevoinfo.obb"
fi

echo ""
read -p "Продолжить? [Y/n] " CONFIRM
case "$CONFIRM" in
    [nN]) echo "Отменено."; exit 0 ;;
esac

# --- Build the generator ---
echo ""
echo "Building the generator..."
cd "$ROOT_DIR"
./gradlew --quiet installDist

# --- Run ---
echo "Запуск сборки..."
OUTPUT=$(build/install/drevo-db/bin/drevo-db "$MODE" "$MAIN_VERSION" "$PATCH_VERSION" 2>&1 | tee /dev/stderr)

# --- Запись результата в build-history.txt ---
STATS_LINE=$(echo "$OUTPUT" | grep "^Count:")
if [ -n "$STATS_LINE" ]; then
    # Размер patch-файла в мегабайтах (округлённый)
    PATCH_FILE="$DB_DIR/patch.${PATCH_VERSION}.ru.drevoinfo.obb"
    if [ -f "$PATCH_FILE" ]; then
        SIZE_BYTES=$(stat -f%z "$PATCH_FILE" 2>/dev/null || stat -c%s "$PATCH_FILE" 2>/dev/null)
        SIZE_MB=$(( (SIZE_BYTES + 524288) / 1048576 ))
    else
        SIZE_MB="?"
    fi

    TODAY=$(date "+%-d.%m.%Y")
    echo "${MAIN_VERSION}/${PATCH_VERSION}	${TODAY} - ${STATS_LINE} - ${SIZE_MB}" >> "$ROOT_DIR/build-history.txt"
    echo ""
    echo "Записано в build-history.txt:"
    tail -1 "$ROOT_DIR/build-history.txt"
fi
