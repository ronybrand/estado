# ADR 0011: Terraform pra infra existente via import, state local, sem Terragrunt

## Status
Aceito

## Contexto
As ADRs 0001-0010 documentam o raciocínio por trás de cada recurso AWS, mas os recursos em si
sempre foram clicados manualmente (Fase 1 do plano de deploy) ou criados via `aws` CLI direto —
nunca existiram como código revisável. Decisão consciente: terraformar a infra existente, não
recriá-la, dado que já roda produção real com dados reais.

## Decisão
Terraform organizado em dois módulos: `portfolio-instance` (EC2, Security Group, Elastic IP, alarme
CloudWatch — singleton compartilhado entre apps do portfólio) e `app-backup` (bucket S3 + IAM
role/instance profile, parametrizado por `app_name` — reusa o padrão pra apps futuros sem duplicar
código). Os 11 recursos reais foram trazidos via `terraform import`, um a um, e cada atributo do
`.tf` foi ajustado contra o estado real da conta (não escrito de memória) até `terraform plan`
mostrar zero criações/destruições — só então qualquer `apply` foi considerado seguro.

State fica local por enquanto (`terraform/`, gitignored) — projeto de um operador só, numa máquina
só.

## Alternativas consideradas
- **CloudFormation ou CDK**: ficariam mais "nativos" da AWS, mas Terraform é a ferramenta mais
  usada fora do ecossistema de uma nuvem só, e é a que já tinha familiaridade prévia — sem ganho
  real em trocar pra essa escala.
- **Recriar do zero em vez de importar**: mais simples de escrever, mas destruiria e recriaria uma
  instância com Postgres em produção — risco e downtime desnecessários quando importar preserva
  tudo. O backup (ADR 0006) e o snapshot do ADR 0010 dariam uma rede de segurança, mas trocar tudo
  de propósito só pra evitar o trabalho de importar não se justifica.
- **Backend remoto S3 desde o início**: mais correto pra colaboração/CI, mas cria um problema de
  bootstrap (precisa de um bucket pra guardar o state de um bucket) e resolve um problema que não
  existe ainda — um operador só, numa máquina só. Fica como upgrade natural, não pré-requisito
  (mesmo raciocínio de proporcionalidade do ADR 0002/0007).
- **Terragrunt por cima do Terraform**: usado antes em outro contexto (múltiplos ambientes/contas na
  PPRO), mas o valor dele é eliminar repetição entre *múltiplos* state/root modules (backend,
  provider, versionamento repetidos por ambiente). Este projeto tem um state só, um ambiente só — e
  mesmo o próximo app do portfólio entra como módulo dentro do mesmo root (`module
  "outroapp_backup"` em `main.tf`), não como um root novo. Terragrunt aqui seria indireção sem
  contrapartida. Reconsiderar se algum dia existir mais de um state de verdade.

## Consequências
- Positivo: infra revisável num PR, não só documentada em prosa — fecha o gap mais citado entre
  "documentei a decisão" (ADRs 0001-0010) e "o recurso em si é código".
- Positivo: o processo de import pegou um bug real antes de qualquer `apply` — a `description` do
  Security Group escrita de memória não batia com o valor real, e como esse campo é imutável na
  AWS, um apply teria forçado destroy+recreate do SG de produção. Corrigido só comparando contra o
  estado real antes de aplicar.
- Negativo aceito: state local é ponto único de falha do próprio Terraform (não da infra que ele
  gerencia) — se o laptop for perdido sem backup do arquivo, o vínculo entre `.tf` e os recursos
  reais precisa ser reconstruído via import de novo. Risco baixo pro estágio atual, mas real.
- Limite explícito do que o Terraform cobre: só recursos que a AWS sabe que existem (instância,
  rede, IAM, S3, alarme). O que roda *dentro* da instância (`deploy.sh`, `docker-compose.yml`,
  units systemd) continua fora, versionado em `deploy/` e aplicado manualmente por decisão do
  [ADR 0004](0004-deploy-pull-via-systemd-timer.md) — Terraform não gerencia esse lado, de
  propósito, não por lacuna.

**Atualização:** a alternativa "backend remoto S3 desde o início", descartada acima como resolvendo
um problema que não existia ainda, deixou de ser hipotética quando checagem automatizada de drift
em CI virou um requisito real — ver [ADR 0015](0015-terraform-backend-s3-drift-check-ci.md).
