# Estado
Projeto CRUD de unidades federativas do Brasil (estados).

O Projeto Estado trata-se de um sistema sob arquitetura Java 25/Spring Boot 4, configuração de dependência em Maven e banco de dados PostgreSQL para disponibilização de um serviço HTTP. O front-end (Angular) é servido pelo próprio jar, embutido em `src/main/resources/static`.

**No ar**: https://54.94.231.248.sslip.io/ — deploy próprio na AWS (EC2 + Docker + Caddy), com CI/CD,
backup automático e recuperação de falhas. A história completa da migração e do deploy, incluindo
os bugs encontrados em produção e as decisões de arquitetura, está em [`CASE_STUDY.md`](CASE_STUDY.md)
e em [`docs/adr/`](docs/adr/).

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
http://localhost:8080/ (interface Angular)

A API fica em http://localhost:8080/estado

# 4 - Docker
Também dá pra buildar e rodar via container, sem instalar Maven/JDK localmente:
```
docker build -t estado .
docker run -p 8080:8080 -e JDBC_DATABASE_URL=... -e JDBC_DATABASE_USERNAME=... -e JDBC_DATABASE_PASSWORD=... estado
```
A imagem publicada em produção fica em `ghcr.io/ronybrand/estado` (publicada automaticamente a
cada push na `master`, ver [`.github/workflows/docker-publish.yml`](.github/workflows/docker-publish.yml)).

# 5 - Produção
https://54.94.231.248.sslip.io/ — detalhes do deploy em [`CASE_STUDY.md`](CASE_STUDY.md).
