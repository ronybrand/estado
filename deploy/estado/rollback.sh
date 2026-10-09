#!/usr/bin/env bash
# Rollback manual pra uma versao anterior conhecida-boa — ver ADR 0008.
# Reusa o mesmo swap com health check do deploy.sh: se a tag de rollback
# tambem nao ficar saudavel, o container atual continua no ar.
#
# Uso:
#   ./rollback.sh          # usa a tag gravada em last-good-tag pelo ultimo deploy.sh
#   ./rollback.sh <sha>    # usa uma tag/sha especifica ja publicada no GHCR
set -euo pipefail
cd "$(dirname "$0")"
set -a
source .env
set +a
source ./lib-swap.sh

CURRENT="estado-app"
NEXT="estado-app-next"

TAG="${1:-}"
if [ -z "$TAG" ]; then
    if [ ! -f last-good-tag ]; then
        echo "Nenhuma tag informada e last-good-tag nao existe ainda (precisa de pelo menos um deploy.sh bem-sucedido antes)." >&2
        echo "Uso: ./rollback.sh <sha-da-tag-no-ghcr>" >&2
        exit 1
    fi
    TAG="$(cat last-good-tag)"
fi

IMAGE="ghcr.io/ronybrand/estado:${TAG}"
echo "Rollback para $IMAGE"
if ! docker pull "$IMAGE" >/dev/null 2>&1; then
    # Sem acesso ao registry, ou tag so local (./rollback.sh anterior): usa a
    # imagem que ja esta no disco. Pela revisao de last-good-tag, so aceita a
    # "anterior" se for exatamente aquela revisao (depois de um rollback ela
    # aponta pra versao ruim, de onde se saiu).
    ANTERIOR="${IMAGE%%:*}:anterior"
    ANTERIOR_REVISION="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$ANTERIOR" 2>/dev/null || true)"
    if docker image inspect "$IMAGE" >/dev/null 2>&1; then
        echo "Registry indisponivel ou tag so local: usando a imagem local $IMAGE"
        if [ "$TAG" = "anterior" ]; then
            TAG="$(image_revision "$IMAGE")"
        fi
    elif [ -n "$ANTERIOR_REVISION" ] && [ "$ANTERIOR_REVISION" = "$TAG" ]; then
        echo "Registry indisponivel: usando a imagem local $ANTERIOR (mesma revisao $TAG)"
        IMAGE="$ANTERIOR"
    else
        echo "Nao foi possivel baixar $IMAGE e ela nao existe localmente." >&2
        exit 1
    fi
fi

if swap_to "$IMAGE"; then
    promote
    echo "Rollback concluido: $CURRENT agora roda $IMAGE"
    annotate_deploy "Rollback: estado-app -> ${TAG}" '["deploy","estado","rollback"]'
    notify_github_deployment "$TAG" success "Rollback concluido"
    drenar_antigo
else
    notify_github_deployment "$TAG" failure "Rollback abortado, imagem nao ficou saudavel"
    echo "Rollback abortado: $IMAGE tambem nao ficou saudavel. Investigar manualmente antes de tentar outra tag." >&2
    exit 1
fi
