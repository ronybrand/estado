# Sourced por deploy.sh e rollback.sh — sobe uma imagem com nome temporario,
# espera /actuator/health, so entao deixa o chamador substituir o container
# atual. Nao tem shebang de proposito: nunca e executado diretamente.
#
# Requer no ambiente: CURRENT, NEXT, POSTGRES_DB, POSTGRES_USER,
# POSTGRES_PASSWORD, API_ORIGIN_PERMITIDA (via .env, ja carregado pelo
# chamador).

swap_to() {
    local image="$1"

    docker rm -f "$NEXT" >/dev/null 2>&1 || true

    docker run -d \
        --name "$NEXT" \
        --restart unless-stopped \
        --network internal \
        -e JDBC_DATABASE_URL="jdbc:postgresql://postgres:5432/${POSTGRES_DB}" \
        -e JDBC_DATABASE_USERNAME="${POSTGRES_USER}" \
        -e JDBC_DATABASE_PASSWORD="${POSTGRES_PASSWORD}" \
        -e API_ORIGIN_PERMITIDA="${API_ORIGIN_PERMITIDA}" \
        "$image" >/dev/null

    docker network connect portfolio "$NEXT"

    local healthy=false
    for _ in $(seq 1 60); do
        if docker exec "$NEXT" wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
            healthy=true
            break
        fi
        sleep 1
    done

    if [ "$healthy" != true ]; then
        echo "Health check nao ficou UP em 60s, descartando container novo."
        docker logs "$NEXT" --tail 50 || true
        docker rm -f "$NEXT" >/dev/null 2>&1 || true
        return 1
    fi

    return 0
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
