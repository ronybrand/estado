#!/usr/bin/env bash
# pg_dump diario para S3, write-only — ver ADR 0006.
# Roda via estado-backup.timer (diario, 06:00 UTC). Credenciais AWS vem da
# IAM role da instancia (estado-backup-role), nao de chave fixa.
set -euo pipefail
cd "$(dirname "$0")"
set -a
source .env
set +a

BUCKET="estado-db-backups-70a63b1a"
REGION="sa-east-1"
MIN_BYTES=100  # abaixo disso, o dump esta suspeito de vazio/quebrado
TIMESTAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
FILENAME="estado-${TIMESTAMP}.sql.gz"
TMPFILE="/tmp/${FILENAME}"

docker exec estado-postgres-1 pg_dump -U estado estado | gzip > "$TMPFILE"

SIZE="$(stat -c%s "$TMPFILE")"
if [ "$SIZE" -lt "$MIN_BYTES" ]; then
    echo "Dump suspeito de vazio (${SIZE} bytes), abortando upload." >&2
    rm -f "$TMPFILE"
    exit 1
fi

aws s3 cp "$TMPFILE" "s3://${BUCKET}/${FILENAME}" --region "$REGION" --only-show-errors
rm -f "$TMPFILE"
echo "Backup enviado: ${FILENAME} (${SIZE} bytes)"
