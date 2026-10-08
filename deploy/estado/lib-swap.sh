# Sourced por deploy.sh e rollback.sh — sobe uma imagem com nome temporario,
# espera /actuator/health, so entao deixa o chamador substituir o container
# atual. Sem shebang de proposito: nunca e executado diretamente.
#
# O health check roda de FORA do container (um curlimages/curl efemero na
# rede portfolio, resolvendo o nome do container via DNS interno do Docker)
# em vez de "docker exec ... wget localhost" — assim tambem valida que a
# rede/DNS do Compose estao ok, nao so que o processo subiu.
#
# Requer no ambiente: CURRENT, NEXT, POSTGRES_PASSWORD, API_ORIGIN_PERMITIDA,
# ADMIN_PASSWORD_HASH, JWT_SECRET, ASK_API_KEY, ASK_API_BASE_URL (via .env,
# carregado com "set -a" pelo chamador). RATE_LIMIT_PROXY_SECRET e opcional.

# A instancia e pequena (t3.micro, 1 GB) e a JVM padrao (G1, heap = 1/4 da RAM)
# ocupava ~360 MB aqui. Durante o rolling swap dois containers coexistem, entao
# o teto de heap/memoria precisa caber duas vezes. Sobrescreve-se via .env.
APP_JAVA_OPTS="${APP_JAVA_OPTS:--XX:+UseSerialGC -Xmx192m -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m}"
APP_MEMORY_LIMIT="${APP_MEMORY_LIMIT:-384m}"

swap_to() {
    local image="$1"

    docker rm -f "$NEXT" >/dev/null 2>&1 || true

    docker run -d --name "$NEXT" \
        --restart unless-stopped \
        --network estado_internal \
        --memory "$APP_MEMORY_LIMIT" \
        -e JAVA_TOOL_OPTIONS="$APP_JAVA_OPTS" \
        -e JDBC_DATABASE_URL="jdbc:postgresql://postgres:5432/estado" \
        -e JDBC_DATABASE_USERNAME=estado \
        -e JDBC_DATABASE_PASSWORD="$POSTGRES_PASSWORD" \
        -e API_ORIGIN_PERMITIDA="$API_ORIGIN_PERMITIDA" \
        -e ADMIN_USERNAME="${ADMIN_USERNAME:-admin}" \
        -e ADMIN_PASSWORD_HASH="$ADMIN_PASSWORD_HASH" \
        -e JWT_SECRET="$JWT_SECRET" \
        -e ASK_API_KEY="$ASK_API_KEY" \
        -e ASK_API_BASE_URL="$ASK_API_BASE_URL" \
        -e RATE_LIMIT_PROXY_SECRET="${RATE_LIMIT_PROXY_SECRET:-}" \
        -e SPRINGDOC_API_DOCS_ENABLED=false \
        -e SPRINGDOC_SWAGGER_UI_ENABLED=false \
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

# Registra o resultado do swap na aba Deployments do GitHub. O workflow so
# publica a imagem; quem sabe se o deploy deu certo e esta instancia (pull
# via timer, ADR 0004), entao ela mesma avisa. Melhor esforco, como
# annotate_deploy: sem GITHUB_DEPLOY_TOKEN, ou com a API fora do ar, o
# deploy nao falha. Token fine-grained so com "Deployments: write" no repo.
# Uso: notify_github_deployment <sha-completo> <success|failure> <descricao>
notify_github_deployment() {
    local sha="$1"
    local state="$2"
    local descricao="$3"
    local api="https://api.github.com/repos/ronybrand/estado/deployments"

    if [ -z "${GITHUB_DEPLOY_TOKEN:-}" ] || [ -z "$sha" ]; then
        return 0
    fi

    local resposta id
    resposta="$(curl -sf --max-time 10 -X POST "$api" \
        -H "Authorization: Bearer ${GITHUB_DEPLOY_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        -d "{\"ref\":\"${sha}\",\"environment\":\"production\",\"auto_merge\":false,\"required_contexts\":[],\"description\":\"${descricao}\"}" \
        2>/dev/null)" || return 0
    id="$(echo "$resposta" | sed -n 's/^  "id": *\([0-9][0-9]*\),.*/\1/p' | head -1)"
    [ -n "$id" ] || return 0

    curl -sf --max-time 10 -X POST "${api}/${id}/statuses" \
        -H "Authorization: Bearer ${GITHUB_DEPLOY_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        -d "{\"state\":\"${state}\",\"environment_url\":\"https://54.94.231.248.sslip.io/estado\",\"description\":\"${descricao}\"}" \
        >/dev/null 2>&1 || true
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
