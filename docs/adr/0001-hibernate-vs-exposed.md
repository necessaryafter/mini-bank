# 0001 - Hibernate vs. Exposed: porque usar Exposed?

## Status
Aceito

## Contexto
O Hibernate claramente não foi feito para Kotlin. Existe compatibilidade Java <-> Kotlin, mas dá pra sentir que a
linguagem não foi pensada pra esse uso.

O maior problema é a falta de suporte a **value classes**: as entidades precisam ser **classes** comuns, não
**data classes** (o padrão em Kotlin). Procurando uma alternativa mais idiomática e ainda compatível com Spring,
lembrei do Exposed — que tem inclusive um plugin oficial de integração com Spring Boot.

## Decisão
Troquei todas as entidades já criadas em Hibernate para Exposed. Ficaram mais idiomáticas e mais fáceis de usar
no ambiente Kotlin escolhido para esse desafio.

## Consequências
O Hibernate é um ORM bem mais antigo e battle-tested que o Exposed. Tecnicamente, ele resolve algumas coisas que
o Exposed ainda não tem:

#### **Dirty checking automático**
Ao mudar um campo de uma entidade gerenciada e dar commit, o Hibernate já gera o `UPDATE` sozinho. Dá até pra
plugar handlers em cima disso (`@LastModifiedDate`, `@EntityListeners`) para atualizar timestamps automaticamente.

#### **Cascade / Orphan Removal**
`cascade = ALL` e `orphanRemoval = true` em `@OneToMany` fazem o Hibernate apagar os filhos junto com o pai
automaticamente. No Exposed isso não existe: deletar um `Transaction`, por exemplo, não apaga as
`TransactionEntry` associadas. Isso vira responsabilidade manual da aplicação (ou de uma constraint
`ON DELETE CASCADE` no banco).

#### **Lazy loading transparente e fetch joins declarativos**
O JPA usa proxies para carregar relações sob demanda de forma transparente, e permite `JOIN FETCH` para evitar
N+1 de forma declarativa. No Exposed, `referrersOn`/`referencedOn` disparam uma query a cada acesso — evitar
N+1 exige escrever o join manualmente na DSL.

#### **Spring Data JPA**
Perdemos `JpaRepository`, `Pageable`, `Specification` e query methods derivados por nome
(`findByOwnerNameAndStatus`). Toda query passa a ser escrita à mão na DSL do Exposed: mais verboso, porém mais
explícito sobre o SQL gerado.

#### **Bean Validation integrada**
Anotações como `@NotNull`/`@Size`, validadas automaticamente antes do flush, não têm equivalente. Invariantes
viram responsabilidade do domínio ou de constraints do próprio banco (ex: `CHECK (amount > 0)` já usado nas
migrations).

#### **Cache de segundo nível e auditoria pronta (Hibernate Envers)**
Qualquer necessidade de audit trail exige implementação própria.

#### **Ecossistema**
Documentação, exemplos e ferramentas de terceiros são bem mais escassos do que no Hibernate/JPA, por ser um
projeto menor e mais novo.

Em contrapartida, o que ganhamos trocando para o Exposed:

- Sem proxies enganosos nem `LazyInitializationException`.
- Controle total e explícito sobre a query gerada.
- Suporte nativo a value classes via `Column.transform()`, usado para persistir `Money` e `IdempotencyKey` sem
  o boilerplate de conversores JPA (`@Converter`).
- Integração leve com Spring via `exposed-spring-boot-starter`, sem o peso conceitual completo do JPA/Hibernate.
