# Estado

[![CI](https://github.com/ronybrand/estado/actions/workflows/ci.yml/badge.svg)](https://github.com/ronybrand/estado/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/ronybrand/estado/graph/badge.svg)](https://codecov.io/gh/ronybrand/estado)

Projeto CRUD de unidades federativas do Brasil (estados).

O Projeto Estado trata-se de um sistema sob arquitetura Java 25/Spring Boot 4, configuração de dependência em Maven e banco de dados PostgreSQL para disponibilização de um serviço HTTP. O front-end (Angular, repo `angular_estado` separado) é servido estático via S3 + CloudFront, com a API acessível em `/api/*` sob o mesmo domínio (ver ADR 0013).

**No ar**: https://d3bqbg07tehy1h.cloudfront.net/ (frontend, S3 + CloudFront) · API em
https://54.94.231.248.sslip.io/estado (acessível também via `/api/estado` sob o mesmo domínio do
CloudFront) — deploy próprio na AWS (EC2 + Docker + Caddy pro backend, S3 + CloudFront pro frontend),
com CI/CD, backup automático e recuperação de falhas. Infra provisionada via Terraform (importada da
conta real, não escrita do zero — ver [`terraform/`](terraform/)). A história completa da migração e
do deploy, incluindo os bugs encontrados em produção e as decisões de arquitetura, está em
[`CASE_STUDY.md`](CASE_STUDY.md) e em [`docs/adr/`](docs/adr/).

## Arquitetura

```mermaid
flowchart LR
    Browser["Navegador"]

    subgraph CloudFront["CloudFront"]
        direction LR
        S3["S3\n(bundle Angular)"]
        Caddy["Caddy\n(reverse proxy)"]
    end

    subgraph EC2["EC2 (Docker)"]
        direction LR
        App["estado-app\n(Spring Boot)"]
        DB[("Postgres")]
        Alloy["Grafana Alloy"]
    end

    Grafana["Grafana Cloud"]

    Browser -- "/ (estático)" --> S3
    Browser -- "/api/*" --> Caddy
    Caddy --> App
    App --> DB
    Alloy -- "scrape /actuator/prometheus" --> App
    Alloy -- métricas/logs --> Grafana
```

Um único EC2 roda os três containers Docker (app, Postgres, Alloy) via Compose +
`deploy.sh`, com rolling swap sem downtime disparado por um timer systemd a cada
5min, sempre que uma nova imagem chega no GHCR (ver [`deploy/`](deploy/) e
`docs/adr/`). O frontend (repo
[`angular_estado`](https://github.com/ronybrand/angular_estado) separado) é
publicado independentemente em S3/CloudFront.

## Estrutura do projeto

```
src/main/java/br/com/rony/spring/boot/estado/
├── Estado.java                    # entidade JPA
├── EstadoController.java          # endpoints REST
├── EstadoService.java             # regra de negócio
├── EstadoRepository.java          # Spring Data JPA
├── Estado{Create,Update}RequestDTO.java  # payloads de entrada, um por operação
├── EstadoDTO.java                 # payload de saída
├── EstadoNaoEncontradoException.java
├── config/       # CORS, filtros de servlet (RequestIdFilter, ActuatorNoCacheFilter), UrlHandlerFilter
├── error/        # CustomGlobalExceptionHandler + ErrorResponseDto
└── property/     # ApiProperty (@ConfigurationProperties)
```

Pacote raiz plano (entidade, controller, service, repository e DTOs juntos, sem
camadas tipo `domain`/`application`/`infra`) por escolha, não por descuido — é
um CRUD de uma entidade só, e separar em camadas aqui só adicionaria indireção
sem ganho real. `config/`, `error/` e `property/` existem à parte porque são
transversais, não parte do fluxo CRUD em si.

Os DTOs de request são um por operação (`Create` vs `Update`) em vez de um DTO
único reaproveitado — ver comentário em `EstadoUpdateRequestDTO`/`EstadoCreateRequestDTO`
pro motivo (cada um corrige um bug real de payload incompleto).

Convenção de comentários (o que vale explicar com comentário vs. o que deve
virar nome) está documentada em [`CLAUDE.md`](CLAUDE.md), não repetida aqui.

## Funcionalidades:
- Cadastrar uma unidade federativa por vez com data e hora do registro;
- Apresentar a lista das unidades federativas;
- Permitir alterar o nome completo e sigla das unidades federativas com data e hora atualizadas;
- Consultar a unidade da federação pelo seu Id;
- Excluir uma unidade da federação passando seu Id.
Observações: Não é permitido inserir/alterar um nome de estado que já exista ou mesmo para sigla.

# 1 - Compilar com Maven e executar local com java -jar

Observação: os passos abaixo foram montandos para Windows.

## 1.1 Pre-requisistos
Para construir e rodar a aplicação você precisa de:
- [JDK 25](https://www.azul.com/downloads/?version=java-25-lts) ou outra distribuição OpenJDK 25
- [Maven 3.6.3+](https://maven.apache.org)
- Um PostgreSQL acessível (local ou remoto)

## 1.2 Passo a passo
1.2.1 - [Baixar o projeto](https://github.com/ronybrand/estado/archive/master.zip)

1.2.2 - Descompacte o zip, entre no diretório descompactado

1.2.3 - Configure as credenciais do banco via variáveis de ambiente (não há mais credenciais fixas no `application.yml`):
```
set JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/estado
set JDBC_DATABASE_USERNAME=<usuario>
set JDBC_DATABASE_PASSWORD=<senha>
```

1.2.4 - Rodar
- Para rodar usando a porta padrão do projeto (8080), execue o comando abaixo:
```
mvn spring-boot:run
```

# 2 - Postman
Para usar o projeto Estado pelo postman siga os seguintes passos:
- [Postman](https://www.postman.com/downloads/)

Importar coleção de testes (contido no item 1.2.1 - <dir_projeto>/src/test/postman):
![Importar o projeto no postman](https://github.com/ronybrand/numero_por_extenso/blob/feature/numero_por_extenso/importar_projeto_postman.png)

Após importar, aparecerão os seguintes testes, favor rodá-los na ordem da imagem:

![Executar testes](https://github.com/ronybrand/estado/blob/feature/estado/sequencia%20de%20execu%C3%A7%C3%A3o%20de%20teste%20no%20postman.png)

# 3 - Navegador - Local
A API fica em http://localhost:8080/estado

Swagger UI (documentação interativa da API) fica em http://localhost:8080/swagger-ui.html — habilitado
por padrão em dev, desligado explicitamente em produção (ver `deploy/estado/lib-swap.sh`).

A interface Angular não roda mais embutida neste jar (ver ADR 0013) — está no repo separado
[`angular_estado`](https://github.com/ronybrand/angular_estado), rodada localmente com `npm start`
(`http://localhost:4200/`, com proxy pra `/api` -> este backend).

# 4 - Docker
Também dá pra buildar e rodar via container, sem instalar Maven/JDK localmente:
```
docker build -t estado .
docker run -p 8080:8080 -e JDBC_DATABASE_URL=... -e JDBC_DATABASE_USERNAME=... -e JDBC_DATABASE_PASSWORD=... estado
```
A imagem publicada em produção fica em `ghcr.io/ronybrand/estado` (publicada automaticamente a
cada push na `master`, ver [`.github/workflows/docker-publish.yml`](.github/workflows/docker-publish.yml)).

# 5 - Produção
https://d3bqbg07tehy1h.cloudfront.net/ (frontend) — API em https://54.94.231.248.sslip.io/estado ou
via `/api/estado` no mesmo domínio do CloudFront. Detalhes do deploy em [`CASE_STUDY.md`](CASE_STUDY.md).

O commit e a versão do build em execução ficam expostos em `/actuator/info`, útil pra confirmar que
um deploy (ou rollback) aplicou o commit esperado sem precisar consultar o log do `deploy.sh`.
