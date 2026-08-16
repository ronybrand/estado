# Sourced por deploy.sh e rollback.sh — sobe uma imagem com nome temporario,
# espera /actuator/health, so entao deixa o chamador substituir o container
# atual. Sem shebang de proposito: nunca e executado diretamente.
#
# O health check roda de FORA do container (um curlimages/curl efemero na
# rede portfolio, resolvendo o nome do container via DNS interno do Docker)
# em vez de "docker exec ... wget localhost" — assim tambem valida que a
# rede/DNS do Compose estao ok, nao so que o processo subiu.
#
# Requer no ambiente: CURRENT, NEXT, POSTGRES_PASSWORD, API_ORIGIN_PERMITIDA
# (via .env, carregado com "set -a" pelo chamador).

swap_to() {
    local image="$1"

    docker rm -f "$NEXT" >/dev/null 2>&1 || true

    docker run -d --name "$NEXT" \
        --restart unless-stopped \
        --network estado_internal \
        -e JDBC_DATABASE_URL="jdbc:postgresql://postgres:5432/estado" \
        -e JDBC_DATABASE_USERNAME=estado \
        -e JDBC_DATABASE_PASSWORD="$POSTGRES_PASSWORD" \
        -e API_ORIGIN_PERMITIDA="$API_ORIGIN_PERMITIDA" \
        "$image" >/dev/null
    docker network connect portfolio "$NEXT"

    if docker run --rm --network portfolio curlimages/curl:8.11.1 sh -c "
        for i in \$(seq 1 30); do
            curl -sf http://${NEXT}:8080/actuator/health >/dev/null 2>&1 && exit 0
            sleep 2
        done
        exit 1
    "; then
        return 0
    fi

    docker logs "$NEXT" --tail 50 2>&1 || true
    docker rm -f "$NEXT" >/dev/null 2>&1 || true
    return 1
}

# sha completo do commit que gerou a imagem, via label OCI padrao (setado
# automaticamente pelo docker/metadata-action no publish, com format=long
# pra bater exatamente com a tag por sha publicada no GHCR).
image_revision() {
    docker inspect "$1" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' 2>/dev/null || true
}

promote() {
    docker rm -f "$CURRENT" >/dev/null 2>&1 || true
    docker rename "$NEXT" "$CURRENT"
}

# Marca no Grafana quando um deploy/rollback aconteceu, pra correlacionar
# visualmente com mudanca de latencia/erro no dashboard (ver ADR 0012).
# Melhor esforco de proposito: GRAFANA_CLOUD_URL/GRAFANA_CLOUD_ANNOTATIONS_TOKEN
# nao configurados, ou Grafana fora do ar, nunca falham o deploy - a
# anotacao e so uma conveniencia de observabilidade, nao faz parte do
# caminho critico do swap.
annotate_deploy() {
    local texto="$1"
    local tags="$2"

    if [ -z "${GRAFANA_CLOUD_URL:-}" ] || [ -z "${GRAFANA_CLOUD_ANNOTATIONS_TOKEN:-}" ]; then
        return 0
    fi

    curl -sf --max-time 5 -X POST "${GRAFANA_CLOUD_URL}/api/annotations" \
        -H "Authorization: Bearer ${GRAFANA_CLOUD_ANNOTATIONS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"text\":\"${texto}\",\"tags\":${tags}}" >/dev/null 2>&1 || true
}
