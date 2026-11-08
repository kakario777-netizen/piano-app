#!/bin/bash
# 빌드할 때 악기 소리 파일(C2~C7)을 받아 앱에 넣는다
set -e
BASE=https://raw.githubusercontent.com/gleitz/midi-js-soundfonts/gh-pages
OUT=app/src/main/assets/sounds
NAMES=(C Db D Eb E F Gb G Ab A Bb B)
# 앱폴더:세트/악기
INSTR=(
  "piano:MusyngKite/acoustic_grand_piano"
  "epiano:FluidR3_GM/electric_piano_1"
  "organ:FluidR3_GM/church_organ"
  "strings:FluidR3_GM/string_ensemble_1"
  "harpsichord:FluidR3_GM/harpsichord"
  "musicbox:FluidR3_GM/music_box"
)
for item in "${INSTR[@]}"; do
  dir=${item%%:*}; src=${item#*:}
  mkdir -p "$OUT/$dir"
  for midi in $(seq 36 96); do
    n=${NAMES[$((midi % 12))]}$((midi / 12 - 1))
    echo "$BASE/$src-mp3/$n.mp3 $OUT/$dir/$midi.mp3"
  done
done | xargs -P 16 -n 2 sh -c 'curl -fsSL --retry 4 -o "$1" "$0"'
ls $OUT/* | wc -l
