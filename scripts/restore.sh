#!/usr/bin/env bash
set -euo pipefail

# Restaura una copia lógica generada por scripts/backup.sh.
# Requiere confirmación explícita para evitar sobrescrituras accidentales.
ARCHIVO="${1:-}"
DB_HOST="${FIXTECH_DB_HOST:-127.0.0.1}"
DB_PORT="${FIXTECH_DB_PORT:-3306}"
DB_NAME="${FIXTECH_DB_NAME:-fixtech_db}"
DB_USER="${FIXTECH_DB_USER:-root}"
DB_PASSWORD="${FIXTECH_DB_PASSWORD:-}"

if [[ -z "$ARCHIVO" || ! -f "$ARCHIVO" ]]; then
    echo "Uso: CONFIRMAR_RESTAURACION=si $0 ruta/al/respaldo.sql.gz" >&2
    exit 1
fi
if [[ "${CONFIRMAR_RESTAURACION:-}" != "si" ]]; then
    echo "La restauración sobrescribe datos. Ejecute con CONFIRMAR_RESTAURACION=si" >&2
    exit 1
fi
if ! [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Nombre de base de datos inválido: $DB_NAME" >&2
    exit 1
fi

command -v mysql >/dev/null 2>&1 || {
    echo "mysql no está instalado" >&2
    exit 1
}

export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

mysql \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    --default-character-set=utf8mb4 \
    -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"

if [[ "$ARCHIVO" == *.gz ]]; then
    gzip -dc "$ARCHIVO" | mysql \
        --host="$DB_HOST" \
        --port="$DB_PORT" \
        --user="$DB_USER" \
        --default-character-set=utf8mb4 \
        "$DB_NAME"
else
    mysql \
        --host="$DB_HOST" \
        --port="$DB_PORT" \
        --user="$DB_USER" \
        --default-character-set=utf8mb4 \
        "$DB_NAME" < "$ARCHIVO"
fi

echo "Restauración completada en $DB_NAME"
