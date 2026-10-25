#!/usr/bin/env bash
set -euo pipefail

# Copia de seguridad lógica de FIXTECH. La contraseña se toma de una variable
# de entorno y nunca se imprime ni se pasa como argumento en la línea de comandos.
DB_HOST="${FIXTECH_DB_HOST:-127.0.0.1}"
DB_PORT="${FIXTECH_DB_PORT:-3306}"
DB_NAME="${FIXTECH_DB_NAME:-fixtech_db}"
DB_USER="${FIXTECH_DB_USER:-root}"
DB_PASSWORD="${FIXTECH_DB_PASSWORD:-}"
BACKUP_DIR="${FIXTECH_BACKUP_DIR:-backups}"

if ! [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Nombre de base de datos inválido: $DB_NAME" >&2
    exit 1
fi

command -v mysqldump >/dev/null 2>&1 || {
    echo "mysqldump no está instalado" >&2
    exit 1
}
command -v gzip >/dev/null 2>&1 || {
    echo "gzip no está instalado" >&2
    exit 1
}

umask 077
mkdir -p "$BACKUP_DIR"
marca="$(date +%Y%m%d_%H%M%S)"
destino="$BACKUP_DIR/${DB_NAME}_${marca}.sql.gz"

export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

mysqldump \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    --single-transaction \
    --quick \
    --triggers \
    --skip-events \
    --no-tablespaces \
    --default-character-set=utf8mb4 \
    "$DB_NAME" | gzip -9 > "$destino"

if [[ ! -s "$destino" ]]; then
    echo "La copia de seguridad quedó vacía" >&2
    rm -f "$destino"
    exit 1
fi

echo "Copia creada: $destino"
