#!/usr/bin/env bash
# Reproduz do zero o que hoje so existe, feito a mao, na instancia atual
# (ver docs/plano-dominio-proprio.md, fase 5.0, item 3). Idempotente: pode
# rodar de novo numa instancia ja configurada sem duplicar nada. Pensado pra
# rodar via SSM (usuario ssm-user) ou SSH (ec2-user) num Amazon Linux 2023
# arm64 novo, antes de instalar os scripts de deploy/estado e deploy/alloy.
#
# Uso: sudo ./bootstrap.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Rodar como root (sudo ./bootstrap.sh)." >&2
  exit 1
fi

# Sempre ec2-user, nunca derivado de $SUDO_USER: as units do systemd ja
# versionadas (deploy/systemd/*.service) tem User=ec2-user e
# WorkingDirectory=/home/ec2-user/... fixos - usar outro usuario aqui
# deixaria os diretorios criados por este script sem bater com o que as
# units esperam. $SUDO_USER e enganoso neste contexto: via SSM
# (AWS-RunShellScript ja roda como root) um "sudo ./bootstrap.sh" extra
# so define SUDO_USER=root, nao o usuario de login real (achado rodando
# este script pela primeira vez, 2026-10-09 - ver docs/plano-dominio-
# proprio.md).
REAL_USER="ec2-user"
REAL_HOME="$(getent passwd "$REAL_USER" | cut -d: -f6)"

echo "==> Usuario alvo: ${REAL_USER} (${REAL_HOME})"

# --- Docker + plugin do Compose -------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  echo "==> Instalando Docker"
  dnf install -y docker
else
  echo "==> Docker ja instalado, pulando"
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "==> Instalando plugin do Docker Compose (arm64)"
  mkdir -p /usr/local/lib/docker/cli-plugins
  COMPOSE_VERSION="v2.39.4"
  curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
else
  echo "==> Docker Compose ja instalado, pulando"
fi

systemctl enable --now docker
usermod -aG docker "$REAL_USER"

# --- Rotacao de logs do Docker (fase 5.0, dimensionamento do disco) -------
# Sem isso, json-file nao tem limite - ver docs/plano-dominio-proprio.md.
DAEMON_JSON=/etc/docker/daemon.json
if [ ! -f "$DAEMON_JSON" ]; then
  echo "==> Configurando rotacao de log do Docker"
  cat > "$DAEMON_JSON" <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF
  systemctl restart docker
else
  echo "==> ${DAEMON_JSON} ja existe, pulando (conferir manualmente se bate com o esperado)"
fi

# --- Redes Docker compartilhadas ------------------------------------------
for rede in portfolio estado_internal; do
  if ! docker network inspect "$rede" >/dev/null 2>&1; then
    echo "==> Criando rede Docker ${rede}"
    docker network create "$rede"
  else
    echo "==> Rede ${rede} ja existe, pulando"
  fi
done

# --- Swap de 1 GB (metade do atual - ver dimensionamento do disco) --------
# vm.swappiness=10 ja usado na instancia atual (uso de swap nunca passou de
# ~160 MB em dias de observacao, 1 GB da folga confortavel).
if [ ! -f /swapfile ]; then
  echo "==> Criando swap de 1 GB"
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo "/swapfile none swap sw 0 0" >> /etc/fstab
else
  echo "==> /swapfile ja existe, pulando"
fi

if ! grep -q "^vm.swappiness" /etc/sysctl.d/99-swappiness.conf 2>/dev/null; then
  echo "==> Ajustando vm.swappiness=10"
  echo "vm.swappiness=10" > /etc/sysctl.d/99-swappiness.conf
  sysctl --system >/dev/null
else
  echo "==> vm.swappiness ja configurado, pulando"
fi

# --- Limites do journald (mesmos valores da instancia atual) --------------
JOURNALD_DROPIN=/etc/systemd/journald.conf.d/estado-limites.conf
if [ ! -f "$JOURNALD_DROPIN" ]; then
  echo "==> Configurando limites do journald"
  mkdir -p /etc/systemd/journald.conf.d
  cat > "$JOURNALD_DROPIN" <<'EOF'
[Journal]
SystemMaxUse=50M
RuntimeMaxUse=30M
EOF
  systemctl restart systemd-journald
else
  echo "==> ${JOURNALD_DROPIN} ja existe, pulando"
fi

# --- dnf clean (fase 5.0, dimensionamento do disco) -----------------------
echo "==> dnf clean all"
dnf clean all

# --- Diretorios de app -----------------------------------------------------
for dir in estado estado-ai-agent proxy alloy; do
  if [ ! -d "${REAL_HOME}/${dir}" ]; then
    echo "==> Criando ${REAL_HOME}/${dir}"
    mkdir -p "${REAL_HOME}/${dir}"
    chown "${REAL_USER}:${REAL_USER}" "${REAL_HOME}/${dir}"
  else
    echo "==> ${REAL_HOME}/${dir} ja existe, pulando"
  fi
done

# --- Units e timers do systemd ---------------------------------------------
# Copiados daqui, mas nao habilitados sozinhos: o enable acontece depois que
# os .env/scripts reais tiverem sido instalados (ver deploy/README.md),
# senao os timers disparam contra um deploy/backup ainda incompleto.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
if [ -d "${REPO_ROOT}/deploy/systemd" ]; then
  echo "==> Copiando units do systemd (nao habilitadas ainda)"
  cp "${REPO_ROOT}"/deploy/systemd/estado-*.service "${REPO_ROOT}"/deploy/systemd/estado-*.timer /etc/systemd/system/
  systemctl daemon-reload
else
  echo "==> deploy/systemd nao encontrado relativo a este script, pulando (copiar manualmente)"
fi

# --- Grafana Alloy (RPM aarch64) -------------------------------------------
if ! command -v alloy >/dev/null 2>&1; then
  echo "==> Instalando Grafana Alloy"
  curl -s -o /tmp/gpg.key https://rpm.grafana.com/gpg.key
  rpm --import /tmp/gpg.key
  cat > /etc/yum.repos.d/grafana.repo <<'EOF'
[grafana]
name=grafana
baseurl=https://rpm.grafana.com
repo_gpgcheck=1
enabled=1
gpgcheck=1
gpgkey=https://rpm.grafana.com/gpg.key
sslverify=1
sslcacert=/etc/pki/tls/certs/ca-bundle.crt
EOF
  dnf install -y alloy
  usermod -aG docker alloy
  usermod -aG systemd-journal alloy
else
  echo "==> Alloy ja instalado, pulando"
fi

ALLOY_DROPIN=/etc/systemd/system/alloy.service.d/override.conf
if [ ! -f "$ALLOY_DROPIN" ]; then
  echo "==> Configurando EnvironmentFile do Alloy (${REAL_HOME}/alloy/.env)"
  mkdir -p /etc/systemd/system/alloy.service.d
  echo -e "[Service]\nEnvironmentFile=${REAL_HOME}/alloy/.env" > "$ALLOY_DROPIN"
  systemctl daemon-reload
else
  echo "==> ${ALLOY_DROPIN} ja existe, pulando"
fi

cat <<EOF

==> Bootstrap concluido. Passos manuais restantes (ver deploy/README.md):
    1. Copiar deploy/estado/, deploy/proxy/, deploy/alloy/config.alloy e os
       .env reais (nunca pelo bootstrap - segredo nao entra em script
       versionado).
    2. Preencher ${REAL_HOME}/alloy/.env (chmod 600) e so entao:
       systemctl enable --now alloy
    3. Depois dos .env/compose/scripts no lugar:
       systemctl enable --now estado-deploy.timer estado-backup.timer estado-prune.timer
    Nao baixar jvm-latest nesta maquina (so existe no GHCR pra rollback
    pontual) - ver docs/plano-dominio-proprio.md, dimensionamento do disco.
EOF
