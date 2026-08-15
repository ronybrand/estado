#!/usr/bin/env bash
# pg_dump diario para S3, write-only — ver ADR 0006.
# Roda via estado-backup.timer (diario, 06:00 UTC). Credenciais AWS vem da
# IAM role da instancia (estado-backup-role), nao de chave fixa.
set -euo pipefail
cd "$(dirname "$0")"
source .env

BUCKET="estado-db-backups-70a63b1a"
REGION="sa-east-1"
MIN_BYTES=200  # abaixo disso, o dump esta suspeito de vazio/quebrado
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="/tmp/estado-${TIMESTAMP}.sql.gz"

docker compose exec -T postgres pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" | gzip > "$DUMP_FILE"

SIZE="$(stat -c%s "$DUMP_FILE")"
if [ "$SIZE" -lt "$MIN_BYTES" ]; then
    echo "Dump suspeito de vazio (${SIZE} bytes), abortando sem enviar."
    rm -f "$DUMP_FILE"
    exit 1
fi

aws s3 cp "$DUMP_FILE" "s3://${BUCKET}/estado-${TIMESTAMP}.sql.gz" --region "$REGION"
rm -f "$DUMP_FILE"
echo "Backup enviado: estado-${TIMESTAMP}.sql.gz (${SIZE} bytes)"
