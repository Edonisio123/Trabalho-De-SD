#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
find src -name '*.java' | sort > fontes.txt
javac -encoding UTF-8 -d out @fontes.txt
printf 'Compilação concluída. Classes em out/\n'
