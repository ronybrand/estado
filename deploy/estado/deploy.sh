#!/usr/bin/env bash
# Rolling swap sem downtime — ver ADR 0005.
# Roda via estado-deploy.timer a cada 5 minutos, ou manualmente: ~/estado/deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
source .env

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

docker rm -f "$NEXT" >/dev/null 2>&1 || true

docker run -d \
    --name "$NEXT" \
    --restart unless-stopped \
    --network internal \
    -e JDBC_DATABASE_URL="jdbc:postgresql://postgres:5432/${POSTGRES_DB}" \
    -e JDBC_DATABASE_USERNAME="${POSTGRES_USER}" \
    -e JDBC_DATABASE_PASSWORD="${POSTGRES_PASSWORD}" \
    -e API_ORIGIN_PERMITIDA="${API_ORIGIN_PERMITIDA}" \
    "$IMAGE" >/dev/null

docker network connect portfolio "$NEXT"

healthy=false
for _ in $(seq 1 60); do
    if docker exec "$NEXT" wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        healthy=true
        break
    fi
    sleep 1
done

if [ "$healthy" != true ]; then
    echo "Health check nao ficou UP em 60s, descartando container novo. Versao atual continua no ar."
    docker logs "$NEXT" --tail 50 || true
    docker rm -f "$NEXT" >/dev/null 2>&1 || true
    exit 1
fi

docker rm -f "$CURRENT" >/dev/null 2>&1 || true
docker rename "$NEXT" "$CURRENT"
echo "Deploy concluido: $CURRENT agora roda $IMAGE ($NEW_ID)"
