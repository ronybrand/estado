#!/usr/bin/env bash
# Rolling swap sem downtime — ver ADR 0005.
# Roda via estado-deploy.timer a cada 5 minutos, ou manualmente: ~/estado/deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
source .env
source ./lib-swap.sh

IMAGE="ghcr.io/ronybrand/estado:latest"
CURRENT="estado-app"
NEXT="estado-app-next"

docker pull "$IMAGE"

NEW_ID="$(docker image inspect "$IMAGE" --format '{{.Id}}')"
CURRENT_ID="$(docker inspect "$CURRENT" --format '{{.Image}}' 2>/dev/null || echo '')"

if [ "$NEW_ID" = "$CURRENT_ID" ]; then
    echo "Imagem inalterada, nada a fazer."
    exit 0
fi

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
    echo "Deploy concluido: $CURRENT agora roda $IMAGE ($NEW_ID)"

    if [ -n "$PREVIOUS_TAG" ]; then
        echo "$PREVIOUS_TAG" > last-good-tag
        echo "Tag anterior registrada em last-good-tag: $PREVIOUS_TAG"
    fi
else
    echo "Deploy abortado, versao atual continua no ar."
    exit 1
fi
