# CLAUDE.md — Controle de saldo de itens e pedidos de órgãos públicos

> Desafio técnico (Full Stack Pleno) de um processo seletivo. A avaliação pesa mais
> **decisões e justificativas** do que quantidade de funcionalidades. Todo código
> gerado precisa ser entendido e defendido pelo autor (Juliano).

## Stack (obrigatória — não trocar)
- Backend: Java 17 + Spring Boot **2.7.x** (javax.*, NÃO jakarta.*), Maven
- Frontend: **Vue 2** + **Vuetify 2** (não usar Vue 3, Composition API do Vue 3 ou Vuetify 3)
- Banco: PostgreSQL 16
- Tudo sobe com `docker compose up` (banco, backend, frontend e a carga de dados)
- Migrações de schema: Flyway
- Testes: JUnit 5 + Testcontainers (Postgres real; nada de H2 para testar concorrência)

## Estrutura
```
backend/    Spring Boot (pacotes por domínio: item, pedido, orgao, carga, common)
frontend/   Vue 2 + Vuetify
docs/       decisões (ADRs curtas), uso-ia.md
data/       CSVs da massa (NÃO versionar — está no .gitignore)
docker-compose.yml
README.md
```

## Regras de negócio (invariantes — valem também para os dados carregados)
1. Item: código único, descrição, unidade, saldo disponível. **Saldo nunca negativo.**
2. Pedido pertence a um órgão, tem ≥ 1 item. **Atendido por inteiro ou não atendido.**
3. Pedido pode ser cancelado; quantidades voltam ao saldo. **Cancela uma vez só.**
4. Muitos usuários simultâneos pedindo os mesmos itens.
5. Dois gestores editando o mesmo item: **nenhuma alteração perdida sem alguém saber.**
6. Integração reenvia a mesma requisição com o mesmo identificador:
   **o mesmo pedido nunca é registrado duas vezes.**

## Como cada regra é garantida (decisões atuais — revisar se mudar)
- R1: `CHECK (saldo >= 0)` no banco + validação no serviço. A constraint é a garantia final.
- R2/R4: registro de pedido numa única transação. Débito atômico por item:
  `UPDATE item SET saldo = saldo - :q WHERE id = :id AND saldo >= :q` — se alguma linha
  afetada = 0, rollback do pedido inteiro. Itens processados **ordenados por id** para
  evitar deadlock entre pedidos concorrentes.
- R3: `UPDATE pedido SET situacao='CANCELADO' ... WHERE id = :id AND situacao='CONFIRMADO'`;
  0 linhas afetadas = já cancelado → erro de negócio (409). Devolução de saldo na mesma transação.
- R5: lock otimista (`@Version`) na entidade Item; conflito → HTTP 409 com o estado atual;
  o front mostra o conflito e deixa o usuário recarregar/reaplicar.
- R6: `UNIQUE (id_externo)`. Reenvio com mesmo id → devolve o pedido já existente
  (resposta idempotente), não cria outro. Tratar a corrida via violação da unique.
- Mensageria e cache: **não usados** (decisão justificada no README). Não adicionar sem discutir.

## Massa de dados legada (achados — ver README seção "Carga")
Arquivos UTF-8, `;`, com cabeçalho. Carga: `COPY` para tabelas `stg_*`, limpeza/validação
em SQL, depois insert nas tabelas finais. Rejeitados vão para tabela de quarentena com motivo.
- 2.000 linhas de `pedidos.csv` com `id_externo` repetido (linhas idênticas = reenvio)
- 300 pedidos de órgão inexistente; 100 pedidos sem itens
- 250 `pedido_itens` com item inexistente; 150 com pedido inexistente
- 200 quantidades inválidas (≤ 0 ou não numéricas)
- Unidades sujas (` UN`, `un`, `Un`, `Cx`...) → normalizar (trim + upper)
- 2.893 itens com `saldo_atual` ≠ `saldo_inicial − Σ pedidos confirmados`
- 328 itens ficariam negativos se o saldo for recalculado
- **Premissa a decidir/registrar:** qual saldo é a verdade e o que fazer com pedidos
  que estouram o saldo.

## Convenções
- Código e nomes em português no domínio (Item, Pedido, Orgao, saldo), inglês no técnico
- DTOs separados das entidades; nunca expor entidade JPA no controller
- Erros de negócio com exceções próprias → `@ControllerAdvice` → JSON padronizado
  (`codigo`, `mensagem`, `detalhes`), com status HTTP coerente (400/404/409/422)
- Consultas paginadas sempre (base tem 1 milhão de pedidos); nada de `findAll()` sem página
- Índices criados nas migrações, junto da consulta que os justifica
- Commits pequenos, mensagem no imperativo, em português

## Comandos
```bash
docker compose up --build           # sobe tudo
cd backend && ./mvnw test           # testes backend
cd frontend && npm run dev          # front em modo dev
```
(atualizar conforme o projeto evoluir)

## Uso de IA (faz parte da avaliação)
- Ao final de cada tarefa relevante, adicionar uma entrada em `docs/uso-ia.md`:
  data, o que foi pedido, o que foi aceito, **o que foi rejeitado/corrigido e por quê**.
- Antes de implementar algo não trivial, propor o plano e esperar aprovação.
- Não inventar premissas silenciosamente: se uma regra for ambígua, perguntar ou
  registrar a premissa em `docs/` e no README.
