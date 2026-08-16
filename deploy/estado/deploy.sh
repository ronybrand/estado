#!/usr/bin/env bash
# Rolling swap sem downtime — ver ADR 0005.
# Roda via estado-deploy.timer a cada 5 minutos, ou manualmente: ~/estado/deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
set -a
source .env
set +a
source ./lib-swap.sh

IMAGE="ghcr.io/ronybrand/estado:latest"
CURRENT="estado-app"
NEXT="estado-app-next"

docker pull "$IMAGE" >/dev/null

CURRENT_ID="$(docker inspect --format '{{.Image}}' "$CURRENT" 2>/dev/null || echo '')"
NEW_ID="$(docker inspect --format '{{.Id}}' "$IMAGE")"

if [ "$CURRENT_ID" = "$NEW_ID" ]; then
    echo "Imagem sem mudanca, nada a fazer."
    exit 0
fi

echo "Nova imagem detectada, subindo container novo ($NEXT)..."

# Guarda a tag da versao que esta rodando agora (presumivelmente boa, ja
# passou por este mesmo health check no deploy anterior) antes de troca-la
# — permite rollback manual rapido se a versao nova tiver um bug funcional
# que nao derruba o health check. Ver ADR 0008 e ./rollback.sh.
PREVIOUS_TAG=""
if [ -n "$CURRENT_ID" ]; then
    PREVIOUS_TAG="$(image_revision "$CURRENT_ID")"
fi

if swap_to "$IMAGE"; then
    promote
    echo "Deploy concluido sem downtime: $CURRENT agora roda $IMAGE ($NEW_ID)"

    if [ -n "$PREVIOUS_TAG" ]; then
        echo "$PREVIOUS_TAG" > last-good-tag
        echo "Tag anterior registrada em last-good-tag: $PREVIOUS_TAG"
    fi

    NEW_TAG="$(image_revision "$NEW_ID")"
    annotate_deploy "Deploy: estado-app -> ${NEW_TAG:-$NEW_ID}" '["deploy","estado"]'
else
    echo "Health check falhou, mantendo versao anterior no ar." >&2
    exit 1
fi
