#!/usr/bin/env bash
# Convierte un video .webm de Playwright en GIF (para documentos que no reproducen video).
# Uso: support/video-a-gif.sh <entrada.webm> <salida.gif> [ancho=960] [fps=6]
# Necesita un ffmpeg completo en el PATH o en FFMPEG (el que trae Playwright solo codifica VP8).
set -euo pipefail
IN=$1; OUT=$2; W=${3:-960}; FPS=${4:-6}; FF=${FFMPEG:-ffmpeg}
"$FF" -v error -y -i "$IN" -vf "fps=$FPS,scale=$W:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" "$OUT"
echo "GIF: $OUT"
