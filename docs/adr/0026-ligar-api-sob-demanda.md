# ADR 0026: Botão "Ligar a API" (Lambda + Turnstile) com desligamento automático

## Status
Aceito e aplicado em produção (2026-10-18)

## Contexto
Para gastar o mínimo, a EC2 pode ficar parada quando o portfólio não está em uso
(`deploy/aws-power.ps1`). Com ela parada, quem abre um frontend vê a API fora do ar e não tem como
religá-la sem acesso à AWS.

## Decisão
Uma Lambda (`estado-start-api`, Python, Function URL pública) serve ela mesma a página com o widget do
Turnstile (GET) e liga a instância quando recebe um token válido (POST). Os frontends só linkam para essa
URL: assim nenhum precisa carregar o widget nem relaxar o próprio CSP (`script-src 'self'`,
`connect-src 'self'`), e os apps mobile abrem a mesma página no navegador. A página consulta
`/actuator/info` (`no-cors`, só pra saber se respondeu) até a API voltar. Um EventBridge a invoca a cada 15 minutos para desligar
a instância que **ela** ligou há mais de 2 horas.

- **Estado em tags da própria instância** (`AutoStartedAt`, `AutoStoppedAt`), sem DynamoDB: o desligamento
  só vale para quem tem `AutoStartedAt` e cuja `LaunchTime` bate com a tag, então ligar na mão
  (`aws-power.ps1 on`) nunca é desligado pela função.
- **Teto de custo:** cooldown de 15 min após um desligamento automático e desligamento em no máximo 2 h.
  Não há concorrência reservada: o limite da conta é baixo e a AWS recusa reservar. No pior caso, uma `t4g.micro` ligada por algumas horas.
- **IAM mínimo:** Start/Stop/Tag só na ARN da instância; `DescribeInstances` não aceita restrição de recurso.
- **Function URL sem auth:** a proteção é o desafio Turnstile validado no servidor, mais o CORS restrito
  aos frontends (CORS não é segurança contra bot, só evita uso acidental de outras origens).

## Alternativas descartadas
- Ligar sozinho em qualquer erro, sem desafio: qualquer crawler religaria a máquina.
- Widget embutido em cada frontend: exigiria relaxar o CSP de cada um e repetir a integração em seis clientes.
- API Gateway com API key: key no frontend é pública, não protege nada e custa mais.

## Consequências
Nova dependência externa (Cloudflare Turnstile, gratuito) e um secret (`turnstile_secret_key`) que passa
pelo state do Terraform como as demais variáveis sensíveis. A instância leva ~1 min para responder
depois de ligada, então o frontend mostra uma contagem e checa `/actuator/info` até ela voltar.
