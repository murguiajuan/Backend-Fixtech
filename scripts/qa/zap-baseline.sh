#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-http://127.0.0.1:8082}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ZAP_OUT_DIR:-$PROJECT_DIR/qa/zap}"
# El puerto de la API se configura con SERVER_PORT; ZAP usa otro puerto por defecto.
ZAP_PORT="${ZAP_PORT:-18090}"
mkdir -p "$OUT_DIR"

marca="$(date +%Y%m%d_%H%M%S)"
reporte="$OUT_DIR/zap_baseline_${marca}.html"

if command -v zap-baseline.py >/dev/null 2>&1; then
    zap-baseline.py -t "$TARGET" -r "$reporte"
elif [[ -n "${ZAP_HOME:-}" && -x "$ZAP_HOME/zap.sh" ]]; then
    "$ZAP_HOME/zap.sh" -cmd \
        -port "$ZAP_PORT" \
        -quickurl "$TARGET" \
        -quickout "$reporte" \
        -quickprogress
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    docker run --rm --network=host \
        -v "$OUT_DIR:/zap/wrk:rw" \
        ghcr.io/zaproxy/zap-stable \
        zap-baseline.py -t "$TARGET" -r "$(basename "$reporte")"
else
    echo "ZAP no está disponible. Instale zap-baseline.py, defina ZAP_HOME o habilite Docker." >&2
    exit 2
fi

echo "Reporte ZAP: $reporte"
