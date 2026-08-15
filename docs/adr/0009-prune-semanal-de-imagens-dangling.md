# ADR 0009: Prune semanal de imagens Docker dangling

## Status
Aceito

## Contexto
Cada troca de `latest` pelo `deploy.sh` (ADR 0005) faz o container correr pra uma imagem nova,
deixando a imagem anterior sem tag (`<none>`) no cache local — o Docker não remove isso sozinho.
Detectado numa auditoria de disco: 24 imagens acumuladas, ~2.2GB, 74% reclamável, depois de poucas
horas de operação normal + os testes do rollback (ADR 0008). Instância em 16% de uso de disco
(26GB livres de 30GB) — sem risco imediato, mas crescimento sem limite eventualmente ameaça a
mesma coisa que motivou o ADR 0006: disco cheio impede o Postgres de escrever.

## Decisão
Um `systemd timer` semanal (`estado-prune.timer`) roda `docker image prune -f` — remove só imagens
*dangling* (sem tag, não referenciadas por nenhum container), nunca uma imagem com tag em uso.

## Alternativas consideradas
- **`docker system prune -a`** (remove também imagens com tag não usadas por nenhum container
  agora): mais agressivo, liberaria mais espaço. Descartado porque removeria as imagens das tags
  por sha que `rollback.sh` (ADR 0008) pode precisar puxar de volta rapidamente — `-a` forçaria
  re-pull do GHCR toda vez, o oposto do que o rollback rápido tenta evitar.
- **Aumentar o volume EBS**: adiaria o problema sem resolver a causa (acumulo sem limite). Também
  custa mais por GB do que simplesmente limpar o que não é mais referenciado.
- **Prune dentro do próprio `deploy.sh`, a cada deploy**: resolveria na origem, mas acopla duas
  responsabilidades (trocar container, limpar cache) no script errado — timer separado, como
  backup, mantém cada mecanismo isolado e failure mode independente.

## Consequências
- Positivo: crescimento de disco por imagens `<none>` passa a ser limitado a no máximo uma semana
  de acumulo entre execuções.
- Positivo: não afeta `rollback.sh` — só remove o que já não tem tag nenhuma, nunca uma imagem que
  uma tag por sha ainda referencia.
- Negativo aceito: `docker image prune -f` sem `--filter until=` remove *toda* imagem dangling no
  momento da execução, inclusive uma que tenha ficado sem tag há poucos minutos — sem consequência
  prática aqui (nada depende de uma imagem sem tag por definição), mas vale registrar que não há
  período de carência.
