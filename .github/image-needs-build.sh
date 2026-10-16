#!/usr/bin/env bash
# Decide se o commit exige publicar imagem nova. Imprime build=true|false em stdout
# (o motivo vai para stderr). Em qualquer dúvida o resultado é build=true: o pior caso
# é o comportamento de antes, publicar de novo, nunca deixar uma mudança sem imagem.
#
# Uso: image-needs-build.sh <imagem sem tag> <sha do commit>
set -uo pipefail

image="$1"
head_sha="$2"

# Tudo que os Dockerfiles copiam e que entra no jar/binário. src/test fica de fora porque
# nenhum dos dois builds o empacota (bootJar e nativeCompile -x test).
image_inputs=(Dockerfile Dockerfile.native gradlew gradlew.bat gradle build.gradle.kts settings.gradle.kts src/main)

build() {
    echo "build=true"
    echo "build=true: $1" >&2
    exit 0
}

[ "${FORCE_BUILD:-}" = "true" ] && build "execução manual"

# Compara com a revisão que cada tag publicada carrega, não com o commit anterior: se um
# commit que mudou a imagem falhar no CI e o seguinte (só docs) passar, o seguinte ainda
# precisa publicar o que o anterior deixou pendente.
for tag in latest jvm-latest; do
    published="$(docker buildx imagetools inspect "$image:$tag" --format '{{json .Image}}' 2>/dev/null \
        | jq -r 'first(.[]).config.Labels["org.opencontainers.image.revision"] // empty' 2>/dev/null)"
    [[ "$published" =~ ^[0-9a-f]{40}$ ]] || build "sem revisão legível em $image:$tag"

    git fetch --no-tags --depth=1 --quiet origin "$published" 2>/dev/null \
        || build "revisão $published de $tag indisponível"

    git diff --quiet "$published" "$head_sha" -- "${image_inputs[@]}"
    case $? in
        0) ;;
        1) build "$tag (${published:0:7}) difere de ${head_sha:0:7} nos arquivos da imagem" ;;
        *) build "git diff falhou para $tag" ;;
    esac
done

echo "build=false"
echo "build=false: nada que entre na imagem mudou desde as revisões publicadas" >&2
