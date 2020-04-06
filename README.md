# Estado
Projeto CRUD de unidades federativas do Brasil (estados).

O Projeto Estado trata-se de um sistema sob arquitetura Java/Spring Boot configuração de depndência em Maven para disponibilização de um serviço HTTP. 

## Funcionalidades:
- Cadastrar uma unidade federativa por vez com data e hora do registro;
- Apresentar a lista das unidades federativas;
- Permitir alterar o nome completo e sigla das unidades federativas com data e hora atualizadas;
- Consultar a unidade da federação pelo seu Id;
- Excluir uma unidade da federação passando seu Id.

# 1 - Compilar com Maven e executar local com java -jar

Observação: os passos abaixo foram montandos para windows.

## 1.1 Pre-requisistos
Para construir e rodaar a aplicação você precisa de:
- [JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Maven 3.6.3](https://maven.apache.org)

## 1.2 Passo a passo
1.2.1 - [Baixar o projeto](https://github.com/ronybrand/estado/archive/feature/estado.zip)

1.2.2 - Descompacte o zip, entre no diretório descompactado e execute na linha de comando:
```
mvn package
```

1.2.3 - Rodar
- Para rodar usando a porta padrão do projeto (8090), execue o comando abaixo:
```
java -jar target\estado-0.0.1-SNAPSHOT.jar
```

- Caso já tenha outro serviço rodando nesta porta (como meu caso). Informe a porta na tag abaixo ex: 8090
```
java -jar -Dserver.port=<porta> target\estado-0.0.1-SNAPSHOT.jar
```
1.2.4 - Abra um navegador e obtenha os números por extenso através da URL demonstrada abaixo. 
- Infome a porta escolhida no passo anterior.
- Informe o número que deseja por extenso. Lembre-se da faixa na descrição do projeto.
```
http://localhost:<porta>/<numero>
```

# 2 - Postman
Para obter número por extenso pelo postman:
- [Postman](https://www.postman.com/downloads/)

Importar coleção de testes (contido no item 1.2.1 - <dir_projeto>/src/test/postman):
![Importar o projeto no postman](https://github.com/ronybrand/numero_por_extenso/blob/feature/numero_por_extenso/importar_projeto_postman.png)

Após importar, aparecerão os seguintes testes, favor rodá-los na ordem da imagem:
![Executar testes](https://github.com/ronybrand/estado/blob/feature/estado/sequencia%20de%20execu%C3%A7%C3%A3o%20de%20teste%20no%20postman.png)
