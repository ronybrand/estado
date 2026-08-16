# ADR 0010: Criptografar o volume EBS raiz (não deixar como risco aceito)

## Status
Aceito

## Contexto
Levantando o inventário de recursos AWS pra terraformar a infra, o volume EBS raiz da instância
(`vol-0b13d2005bf814903`, onde ficam os dados do Postgres) apareceu como não criptografado — nenhuma
ADR mencionava isso. Não era uma decisão consciente: contas AWS novas não ligam "EBS encryption by
default" sozinhas, e ninguém setou o flag na hora de lançar a instância (Fase 1 do plano de deploy).

## Decisão
Criptografar o volume via snapshot: snapshot do volume raiz ainda rodando (crash-consistent, mesmo
nível de risco que o cenário já coberto pelo [ADR 0007](0007-ec2-auto-recovery.md) de falha de
hardware sem shutdown limpo) → cópia do snapshot com `--encrypted` (chave `aws/ebs` gerenciada pela
AWS) → volume novo a partir da cópia → parar a instância, trocar `/dev/xvda` pro volume novo, ligar
de novo. Backup fresco via `backup.sh` tirado antes do procedimento como rede de segurança adicional
além do snapshot em si.

Ao contrário da falha de zona de disponibilidade (risco aceito, custo real de multi-AZ desproporcional
nesse estágio), isso não entrou na lista de riscos aceitos — criptografia de EBS não tem custo
adicional (mesmo $/GB, sem penalidade de performance), então não existe trade-off financeiro
justificando deixar como estava. Era só um default que ninguém tinha olhado.

## Alternativas consideradas
- **Registrar como risco aceito, documentado como o `⚠️` de falha de AZ**: descartado — nomear como
  "decisão" algo que na verdade foi um default nunca revisto, quando o custo de corrigir é zero, é o
  mesmo erro que o [ADR 0006](0006-backup-pg-dump-s3.md) já nomeou sobre os 30 dias de retenção do
  S3: preferível fechar a lacuna a dar aparência de decisão informada pra um esquecimento.
- **Snapshot só depois de parar a instância** (elimina até a hipótese de crash-consistency):
  descartado por aumentar a janela de indisponibilidade sem ganho real — o cenário de
  crash-consistency já é implicitamente aceito pelo ADR 0007 (recuperação de falha de hardware sem
  shutdown limpo), e o Postgres já é projetado pra isso via WAL.
- **Deixar o volume antigo (não criptografado) anexado como volume secundário, só copiando os dados
  por dentro do SO**: mais lento e mais complexo (precisaria montar, rsync, ajustar bootloader) que
  trocar o volume raiz inteiro via AWS, sem vantagem real.

## Consequências
- Positivo: dado do Postgres em repouso agora protegido contra leitura de disco/snapshot fora da
  conta. Sensibilidade real do dado atual é baixa (nome/sigla de UF, sem PII) — o ganho aqui é mais
  de higiene operacional (fechar um default que devia ter sido ligado desde o início) do que de
  proteção de um dado sensível específico.
- Positivo: validado de ponta a ponta — instância voltou, os 3 containers subiram sozinhos via
  `restart: unless-stopped`, os 3 timers systemd continuaram ativos, aplicação respondendo `200` na
  URL real depois do corte.
- Negativo aceito: janela de indisponibilidade real durante o corte (parar → trocar → ligar), ao
  contrário de todo o resto das operações deste projeto (rolling swap, backup, prune), que rodam sem
  downtime. Não tem como evitar pra troca de volume raiz — é uma operação rara (uma vez), não uma
  rotina.
- Volume antigo não criptografado (`vol-0b13d2005bf814903`) e o snapshot não criptografado
  (`snap-010dfc96ef3892704`) ficam retidos como rollback instantâneo por um período antes de serem
  apagados manualmente — custo residual pequeno (~US$0,08/GB-mês em `sa-east-1`) até a decisão de
  apagar.
