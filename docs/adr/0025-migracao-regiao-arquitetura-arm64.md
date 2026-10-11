# ADR 0025: Migração da instância para `us-east-1`/`t4g.micro` (arm64)

## Status
Aceito e aplicado em produção (2026-10-10)

## Contexto
A instância do portfólio rodava em `sa-east-1` (São Paulo), `t3.micro` (x86_64), desde o ADR 0002. O
[ADR 0022](0022-native-image-graalvm.md) já tinha reduzido bastante o footprint de memória do backend
(native image) e viabilizado o downsize de `t3.small` pra `t3.micro`, mas duas limitações continuavam:

- **Custo de região**: `us-east-1` é significativamente mais barata que `sa-east-1` pro mesmo tipo de
  instância, e instâncias `t4g` (Graviton/arm64) custam menos ainda que `t` (x86) equivalentes.
- **Disco**: a instância original usava 30 GB (herdado de uma AMI ECS-optimized, nunca dimensionado de
  proposito — ver ADR 0010). Uso real medido era de só ~7 GB.

## Decisão
Recriar a instância em `us-east-1`, `t4g.micro` (ARM64), disco gp3 de 12 GB, com a app rodando como
imagem native multi-arch (amd64+arm64, CI já publicava multi-arch antes do corte — ver
`.github/workflows/docker-publish.yml` nos repos `estado` e `estado-ai-agent`). Economia estimada:
~US$ 9,4/mês (~US$ 113/ano) contra a `t3.micro` de 30 GB em `sa-east-1`.

**Execução em fases** (plano completo, incluindo todas as decisões de disco/custo, em
[`docs/plano-dominio-proprio.md`](../plano-dominio-proprio.md) — este ADR registra só a decisão e o
resultado, não repete o detalhe operacional):

1. **Prerequisitos** (sem efeito na instância atual): CI multi-arch nos dois repos, módulo Terraform
   `portfolio-instance` generalizado de região (`availability_zone`, `alarm_region` como variáveis,
   `key_name` virou opcional — a instância nova não tem key pair, acesso só por SSM), script de
   bootstrap idempotente (`deploy/bootstrap/bootstrap.sh`).
2. **Instância nova provisionada em paralelo**, sem tráfego (segundo módulo Terraform com
   `providers = aws.us_east_1`). Segredos transferidos máquina-a-máquina via objeto S3 temporário com
   URLs pré-assinadas (nunca passando pelo operador/terminal), apagado (purgado de versão, já que o
   bucket tem versionamento) depois.
3. **Dados restaurados e validados** antes do corte: `pg_dump` lógico + restore, contagem de linhas
   conferida. Confirmado **native arm64 rodando de verdade** (`uname -m` → `aarch64` dentro do
   container, não só cross-compilado num runner de CI).
4. **Corte de DNS**: `api.<domínio>` trocado pro IP novo (TTL 300, propagação em minutos). TLS obtido
   automaticamente pelo Caddy via Let's Encrypt no próprio corte, sem gap perceptível (não foi preciso
   copiar o volume de dados do Caddy).
5. **Observação de 24-48h** antes de desativar a máquina antiga — nesse intervalo, achado um consumidor
   que ainda apontava pro IP antigo diretamente (ver "Achado" abaixo), corrigido antes de prosseguir.
6. **Máquina antiga desativada**: parada, snapshot final do volume raiz tirado, instância + EIP +
   security group + alarme de auto-recovery removidos do Terraform.

## Achado real durante a migração: consumidor direto do IP antigo
O frontend `react_estado` (Vercel) tinha `BACKEND_API_URL` apontando direto pro IP antigo via
`<ip>.sslip.io` — não pelo domínio próprio (`api.<domínio>`), que já existia desde o ADR 0024. O corte
de DNS não afetou esse consumidor porque ele nunca resolvia o domínio, só o IP literal embutido no
hostname sslip.io. Detectado medindo os contadores de rede (`/proc/net/dev`) do Caddy nas duas máquinas
antes de desativar a antiga — sem essa checagem, desativar a instância antiga teria quebrado o
`react_estado` em produção silenciosamente. Corrigido trocando a variável na Vercel pra
`https://api.<domínio>` e redeployando. **Lição**: depois de qualquer corte de DNS, confirmar que todo
consumidor conhecido do serviço (não só o que o corte em si cobre) já usa o hostname estável, não um IP
ou alias ligado à infraestrutura antiga.

## Alternativas consideradas
Documentadas em detalhe em [`docs/plano-dominio-proprio.md`](../plano-dominio-proprio.md) — resumo:
- **Ficar em `sa-east-1`, só trocar o tipo de instância**: descartado, a diferença de preço por região
  é maior que a diferença entre tipos de instância no mesmo tier.
- **Disco de 16 GB em vez de 12 GB**: a diferença de custo (~US$ 0,32/mês) não justificava o risco de
  disco cheio sem as medidas de contenção (rotação de log do Docker, swap reduzido, não baixar a
  imagem JVM de rollback na instância) e um alerta de disco — decisão final: 12 GB, condicionada a
  essas medidas + alerta (ver ADR 0012, seção do alerta de disco).
- **x86 (`t3.micro`) em `us-east-1`**: mais caro que `t4g.micro` pro mesmo tier, sem ganho real já que
  o backend native e o agent (JVM puro) suportam arm64 nativamente.

## Consequências
- Positivo: ~US$ 113/ano de economia estimada.
- Positivo: imagem native rodando em arm64 de verdade validado em produção (não só em CI) — reduz o
  risco de "funciona no x86, quebra no arm" que o plano listava como risco aceito antes do corte.
- Negativo aceito: residência de dados muda de São Paulo pra Virgínia (dado de demonstração, sem
  implicação real de privacidade neste projeto) e latência de chamadas não cacheadas sobe ~100-140ms
  pra visitantes no Brasil — aceitável pro `/ask` (15-30s de resposta do LLM already dominates) e pouco
  perceptível pro CRUD simples.
- Negativo aceito: a instância nova não tem key pair (`key_name = null`) — acesso é só via SSM, nunca
  SSH direto. Consistente com o que já era a prática real (SSM preferido há tempos, ver
  `terraform/ssm.tf`), só formaliza isso pra instância nova.
- Mitigado: o gap de observabilidade/automação na instância nova durante a transição (Alloy e os
  timers de deploy/backup/prune ficaram desligados até o corte de DNS estar confirmado, pra não gerar
  métricas duplicadas nem rodar deploy automático antes de validar o banco) — habilitados assim que o
  corte foi confirmado estável.
