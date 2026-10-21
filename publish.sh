#!/bin/bash

# Publishes the newest main + patch pair in DB/ as the GitHub Release db-<main>-<patch>.
# Run from the repository root (make.sh does it after a build); asks before publishing.
# main is attached only when no earlier release holds it; SHA256SUMS always covers both files and stat.txt.

set -eo pipefail

DB_DIR="$PWD/DB"

newest() {
    ls "$DB_DIR"/"$1".*.ru.drevoinfo.obb 2>/dev/null | sed "s/.*$1\.\([0-9]*\)\..*/\1/" | sort -n | tail -1
}

MAIN=$(newest main)
PATCH=$(newest patch)
if [ -z "$MAIN" ] || [ -z "$PATCH" ]; then
    echo "Error: no main or patch database in $DB_DIR" >&2
    exit 1
fi
MAIN_FILE="main.$MAIN.ru.drevoinfo.obb"
PATCH_FILE="patch.$PATCH.ru.drevoinfo.obb"
TAG="db-$MAIN-$PATCH"

# make.sh records every build here; a missing line means the patch was not built against this main.
STATS=$(grep "^$MAIN/$PATCH	" build-history.txt 2>/dev/null | grep -o "Count:.*)" || true)
if [ -z "$STATS" ]; then
    echo "Error: build-history.txt has no entry for $MAIN/$PATCH, so $PATCH_FILE is not known to be built against $MAIN_FILE" >&2
    exit 1
fi

if gh release view "$TAG" >/dev/null 2>&1; then
    echo "Error: release $TAG is already published" >&2
    exit 1
fi

MAIN_RELEASE=$(gh api "repos/{owner}/{repo}/releases" --paginate \
    --jq ".[] | select(any(.assets[]; .name == \"$MAIN_FILE\")) | .tag_name")
MAIN_RELEASE=${MAIN_RELEASE%%$'\n'*}

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

(cd "$DB_DIR" && shasum -a 256 "$MAIN_FILE" "$PATCH_FILE" stat.txt) > "$WORK_DIR/SHA256SUMS"

FILES=()
if [ -z "$MAIN_RELEASE" ]; then
    FILES+=("$DB_DIR/$MAIN_FILE")
    MAIN_NOTE="$MAIN_FILE is attached here."
else
    MAIN_NOTE="$MAIN_FILE is attached to $MAIN_RELEASE."
fi
FILES+=("$DB_DIR/$PATCH_FILE" "$DB_DIR/stat.txt" "$WORK_DIR/SHA256SUMS")

cat > "$WORK_DIR/notes.md" <<EOF
$PATCH_FILE, built against $MAIN_FILE.

$STATS

$MAIN_NOTE SHA256SUMS covers both databases and stat.txt.
EOF

echo ""
echo "Release $TAG:"
for file in "${FILES[@]}"; do
    echo "  $(basename "$file")"
done
echo "  ($MAIN_NOTE)"
read -r -p "Publish? [y/N] " CONFIRM
case "$CONFIRM" in
    [yY]) ;;
    *) echo "Not published."; exit 0 ;;
esac

gh release create "$TAG" --title "$TAG" --notes-file "$WORK_DIR/notes.md" "${FILES[@]}"
