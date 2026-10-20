#!/bin/bash
set -e

curl -fLO https://drevo-info.ru/drevo2.tar.gz
tar -xzf drevo2.tar.gz
rm -f drevo2.tar.gz

echo "Done."
