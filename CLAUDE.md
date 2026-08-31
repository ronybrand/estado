# CLAUDE.md

Instruções para trabalhar neste repositório.

## Comentários

Comentários explicando o *porquê* (não o *o quê*) já são convenção
estabelecida neste projeto — motivo de uma decisão, um bug que a mudança
corrige, uma restrição não-óbvia. Mantenha isso.

O que evitar: a mesma explicação repetida em mais de um lugar (ex: no DTO/
classe de produção **e** de novo, quase palavra por palavra, no comentário
do teste que prova a mesma coisa). Antes de commitar uma mudança com
comentário novo, procure se a mesma explicação já foi escrita em outro
arquivo do mesmo diff — se sim, mantenha só na fonte mais autoritativa
(normalmente o código de produção, não o teste) e deixe o outro lugar sem
comentário ou com uma referência curta.

Comentário não deve narrar o que o código já diz sozinho. Se dá pra
remover o comentário e nada fica menos claro pra quem lê, ele não deveria
existir — o teste é: o comentário some, e o código continua tão legível
quanto antes? Se sim, é redundante.

Antes de escrever um comentário pra explicar o que uma variável, método ou
valor mágico representa, considere se um nome melhor (extrair um método
com nome descritivo, nomear uma constante, renomear uma variável) resolve
sem precisar de comentário nenhum — clean code: nome substitui comentário
sempre que possível. Comentário é pra motivo (por quê), não pra descrição
(o quê) — descrição vira nome.

## Cobertura

`codecov.yml` define gate de 90% (projeto) / 80% (patch), com `codecov/patch`
obrigatório na branch protection do `master`. `codecov/project` não posta
status check no PR apesar do YAML efetivo em codecov.io bater exatamente com
o do repo e da baseline de cobertura existir (confirmado via API do
Codecov) — não adicione `codecov/project` como obrigatório até isso ser
investigado e confirmado funcionando de verdade, senão todo PR trava sem
nenhum check chegando pra liberar o merge.
