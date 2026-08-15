# ADR 0004: Deploy automático via systemd timer (pull), não GitHub Actions (push)

## Status
Aceito

## Contexto
Depois que o CI passou a publicar a imagem da aplicação no GHCR a cada push na
`master`, faltava automatizar a última etapa: fazer essa imagem nova chegar na
EC2 e substituir a que está rodando. A abordagem inicial foi um job adicional
no GitHub Actions que conecta via SSH na instância e roda o deploy.

## Decisão
Abandonar o job de SSH no GitHub Actions. Em vez disso, um `systemd timer` na
própria EC2 (`estado-deploy.timer`) roda a cada 5 minutos, puxando a imagem
mais recente do GHCR e comparando o digest com o container em execução.

## Alternativas consideradas
- **GitHub Actions conectando via SSH (push)**: implementado primeiro, mas os
  runners do GitHub conectam de IPs dinâmicos da própria infraestrutura deles,
  que não batem com o Security Group (porta 22 restrita só ao IP do
  administrador). A alternativa óbvia — abrir 22 pra `0.0.0.0/0` — foi
  descartada por alargar a superfície de ataque sem necessidade real, só pra
  viabilizar esse mecanismo específico.
- **AWS Systems Manager (SSM) Run Command / Session Manager**: evitaria abrir
  qualquer porta, usando a API da AWS em vez de SSH direto. Não foi adotado
  porque exige configurar OIDC entre GitHub e AWS, um IAM role dedicado e um
  instance profile — mais setup do que o ganho justifica neste estágio, mas é
  a opção mais "correta" se a superfície de automação crescer.

## Consequências
- Positivo: nenhuma porta nova exposta; a única forma de entrar na instância
  continua sendo SSH restrito ao IP do administrador.
- Negativo: deploy não é instantâneo — até 5 minutos de atraso entre o push e
  a atualização em produção. Aceitável pro ritmo de mudanças deste projeto.
- Esse mesmo timer foi depois estendido pra fazer troca sem downtime — ver
  [0005](0005-rolling-swap-sem-canario-blue-green.md).
