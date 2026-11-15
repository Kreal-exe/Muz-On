#!/bin/bash
# Lays a screen recording into the phone of tools/demo/frame.py and writes an animated WebP for the
# README. Record with:  adb shell screenrecord --display-id <id> --bit-rate 20000000 /sdcard/x.mp4
# Usage: tools/demo/make.sh <phone|fold> <recording.mp4> <out.webp> [start-seconds] [length-seconds]
set -euo pipefail
kind=$1; in=$2; out=$3; ss=${4:-0}; len=${5:-60}
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
"${PYTHON:-python3}" "$here/frame.py" "$kind" "$tmp"
read -r x y w h < "$tmp/screen.txt"
ffmpeg -loglevel error -y -ss "$ss" -t "$len" -i "$in" -loop 1 -i "$tmp/bg.png" -loop 1 -i "$tmp/body.png" -filter_complex \
  "[0:v]fps=30,scale=${w}:${h}:flags=lanczos[s];[1:v][s]overlay=${x}:${y}:shortest=1[a];[a][2:v]overlay=0:0:shortest=1,scale=iw/2:ih/2:flags=lanczos" \
  "$tmp/f%05d.png"
# Animated WebP by Pillow (ffmpeg builds often lack libwebp).
"${PYTHON:-python3}" - "$tmp" "$out" <<'PY'
import glob, sys
from PIL import Image
frames = [Image.open(f).convert("RGB") for f in sorted(glob.glob(sys.argv[1] + "/f*.png"))]
frames[0].save(sys.argv[2], save_all=True, append_images=frames[1:], duration=33, loop=0, quality=80, method=4)
PY
echo "$out: $(du -h "$out" | cut -f1)"
