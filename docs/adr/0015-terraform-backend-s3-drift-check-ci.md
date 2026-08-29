# ADR 0015: Backend S3 pro Terraform + drift-check semanal via GitHub Actions

## Status
Aceito

## Contexto
A [ADR 0011](0011-terraform-import-sem-terragrunt.md) decidiu manter o state do Terraform local
(`terraform/`, gitignored), justificado por proporcionalidade: um operador só, numa máquina só,
sem problema real de colaboração ou CI a resolver. Isso deixou de ser hipotético quando checagem
automatizada de drift virou uma necessidade real, não um "seria legal ter" — um workflow do GitHub
Actions não tem acesso ao laptop do operador, então não existe forma de rodar `terraform plan`
periodicamente sem que o state esteja em algum lugar que o CI consiga alcançar.

## Decisão
Backend remoto S3 (`terraform/state-backend.tf`), com locking nativo do S3 (`use_lockfile = true`,
suportado desde Terraform 1.10+) em vez de uma tabela DynamoDB dedicada só pra lock. Bucket com o
mesmo padrão de hardening já usado nos outros buckets do projeto (public access block, SSE-S3),
mais versionamento — aqui ainda mais importante que nos outros, é a rede de segurança contra um
state corrompido ou sobrescrito.

Uma nova IAM role assumida via OIDC (`modules/github-oidc-plan`, mesmo padrão de
`modules/github-oidc-deploy` — reusando o provider OIDC já existente na conta, nunca criando um
segundo) com a política gerenciada `ReadOnlyAccess`, mais uma permissão pontual de leitura/escrita
só no bucket de state (o locking nativo escreve um lockfile mesmo durante um `plan`). Trust policy
restrita ao branch `master` deste repo, mesma condição já usada na role de deploy do frontend.

Workflow semanal (`.github/workflows/terraform-drift-check.yml`, mesma cadência da
[ADR 0009](0009-prune-semanal-de-imagens-dangling.md)) roda `terraform plan -detailed-exitcode`; o
job falha quando há diferença (exit code 2), e o GitHub notifica por e-mail automaticamente — sem
precisar de integração extra tipo Slack pra um projeto de operador só.

## Alternativas consideradas
- **Manter state local, tarefa agendada local (Task Scheduler do Windows)**: preserva a
  proporcionalidade original da ADR 0011 sem tocar nela, mas não é CI de fato — só roda se o
  laptop estiver ligado, e não é o que uma resposta de entrevista sobre "checagem automatizada no
  CI" está descrevendo. Descartada porque o objetivo explícito era ter isso rodando de verdade em
  CI, não simular.
- **DynamoDB pra locking**: era o padrão antes do S3 oferecer locking nativo. Um recurso a mais
  pra gerenciar (tabela, IAM pra ela) sem necessidade, já que a versão do Terraform em uso
  (`required_version >= 1.15`) suporta o mecanismo nativo.
- **Política IAM customizada em vez de `ReadOnlyAccess` gerenciada**: mais granular no papel, mas
  `terraform plan` precisa de `Describe`/`Get`/`List` em praticamente todo serviço usado neste
  projeto (visto ao inventariar todos os `.tf` atuais: EC2, IAM, S3, CloudWatch, CloudFront,
  CloudTrail), e essa lista cresce a cada recurso novo — manutenção contínua sem redução real do
  raio de impacto, já que a role não tem nenhuma permissão de escrita fora do bucket de state.

## Consequências
- Positivo: a resposta "eu adicionaria checagem automatizada de drift no CI" (dada numa entrevista
  técnica) deixou de ser intenção — está rodando, de verdade, semanalmente.
- Positivo: mesmo raciocínio de zero-drift da ADR 0011 (import cuidadoso, plan limpo antes de
  qualquer apply) agora tem verificação contínua, não só no momento em que alguém lembra de rodar
  `terraform plan` manualmente.
- Negativo aceito: o state deixa de estar só na máquina do operador — superfície de exposição
  maior que "só o meu laptop", mitigada por bucket privado (sem acesso público, versionado,
  criptografado) e credenciais de vida curta via OIDC, sem nenhuma access key estática nova
  introduzida na conta.
- Custo adicional: irrelevante — storage do state (poucas centenas de KB, mesmo com versionamento)
  e requests do lockfile nativo somam frações de centavo por mês; minutos de GitHub Actions bem
  dentro da faixa gratuita pra uma execução semanal de ~1-2 minutos.

## Nota de atualização (2026-08-29)

Red-team review desta ADR (issues #10 e #11, corrigidas na PR #12) apertou o que este documento
descrevia como "permissão pontual de leitura/escrita só no bucket de state" e "trust policy
restrita ao branch master": a escrita hoje é restrita ao path do lockfile nativo do S3
(`s3:PutObject`/`s3:DeleteObject` só em `<state_key>.tflock`, não no bucket inteiro), e a trust
policy ganhou uma condição `job_workflow_ref` adicional, restringindo o assume-role ao workflow
`terraform-drift-check.yml` especificamente, não a qualquer workflow deste repo rodando em
`master`. A extração da trust policy pra um module compartilhado com `github-oidc-deploy` (issue
#9, PR #19) também é posterior a este texto.

Também descoberto na primeira execução real do workflow: o `terraform apply` que aplica essas
mudanças de IAM na AWS é manual (não há CD atrelado ao merge de uma PR) — por horas, o código já
tinha a role restringida mas a policy viva na AWS ainda era a antiga, e o próprio drift-check
detectou essa divergência na primeira vez que rodou de ponta a ponta. Decisão consciente de manter
o apply manual (ver recomendação registrada na sessão que fez esse achado): automatizar apply em
push/merge trocaria "esqueci de aplicar" por "um erro de código aplica sozinho em produção",
desproporcional pra um projeto de operador único.
