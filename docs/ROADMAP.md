# Roadmap — Melhorias inspiradas no PlannerFin

## Contexto

Aproximar o app das funcionalidades do [PlannerFin](https://app.plannerfin.com), implementado
**um passo por vez**. Todos os 9 passos abaixo estão concluídos.

---

## Passo 1 — Modelo de status das transações (fundação)

Substituiu a regra antiga (`saldoAjustado = data <= hoje`) por um modelo de status baseado em
três datas na `Transacao`:

| Campo | Significado |
|---|---|
| `dataVencimento` | Quando a conta vence. Alimenta calendário e "atrasada". |
| `dataPagamento` | Quando o dinheiro de fato saiu/entrou. **Dispara o ajuste de saldo.** `null` = não paga. |
| `dataCancelamento` | Quando foi cancelada. Reverte saldo mas preserva histórico. |

Status derivado (não persistido): `PENDENTE` / `PAGA` / `ATRASADA` / `CANCELADA`.

**Quitação é sempre manual** — uma transação futura só afeta `conta.saldo` quando alguém marca
como paga (`PATCH /api/transacoes/{id}/pagar`). O agendador (`AgendadorTransacaoFixa`) não ajusta
saldo sozinho: só pré-cria as parcelas fixas dos próximos 12 meses, todas nascendo PENDENTES.

Endpoints novos: `pagar`, `estornar`, `cancelar` (com `scope` UNICA/GRUPO/FUTURAS).
Migração: `V3__datas_status_transacao.sql`.

---

## Passo 2 — Transferência entre contas

`TipoTransacao.TRANSFERENCIA` + `DirecaoTransferencia` (SAIDA/ENTRADA). Cada transferência vira
duas linhas de `Transacao` (saída na origem, entrada no destino) ligadas por `transferenciaId`,
sempre tratadas em par — pagar/estornar/cancelar/excluir propagam para a linha irmã.
`computeDelta()` passou a considerar direção em vez de assumir RECEITA/DESPESA.

Migração: `V4__transferencia_entre_contas.sql`.

---

## Passo 3 — Cartão de crédito + fatura

Compras no cartão (`ItemFatura`) nunca tocam saldo de conta — ficam soltas até o fechamento.
`AgendadorFatura` roda diariamente e, no dia de fechamento de cada `Cartao`, soma os itens em
aberto e cria **uma única despesa** (débito) na conta de pagamento, com vencimento calculado
(rollover de mês quando `diaVencimento < diaFechamento`). Essa despesa nasce PENDENTE e usa o
**mesmo** `pagar`/`estornar` do Passo 1 — sem endpoint dedicado de "pagar fatura".

Entidades: `Cartao`, `Fatura`, `ItemFatura`. Migração: `V5__cartao_credito_fatura.sql`.

---

## Passo 4 — Orçamento por categoria

`Orcamento` (categoria + mês + limite), com `UNIQUE(espaco_id, categoria_id, mes)` no banco pra
impedir duplicidade. Gasto do mês é somado on-the-fly a partir das transações da categoria
(`idx_transacoes_categoria`).

Migração: `V9__orcamento_por_categoria.sql`.

---

## Passo 5 — Metas financeiras

`Meta` com `valorAtual` mantido incrementalmente (mesmo padrão de `Conta.saldo`). Aportar/resgatar
gera uma `Transacao` real (débito/crédito numa conta escolhida), ligada de volta via `meta_id` —
sem ledger paralelo. Projeção de meses até concluir calculada a partir da média mensal histórica.

Migração: `V10__metas_financeiras.sql`.

---

## Passo 6 — Calendário financeiro

Somente leitura: `GET /api/calendario?mes=` agrega transações por `dataVencimento` (em vez de
`data`/competência). Faturas e parcelas de dívida já são `Transacao` normais, então aparecem de
graça. Nenhuma migração nova.

---

## Passo 7 — Dívidas

`Divida` é um "rótulo" sobre um grupo de parcelas — as parcelas em si são `Transacao` normais
compartilhando `grupoParcelaId` (mesmo mecanismo do parcelamento comum). Cancelar uma dívida
reaproveita `TransacaoService.cancelar(id, "GRUPO")`. Todas as parcelas nascem PENDENTES (é um
acordo, não uma compra já paga).

Migração: `V11__dividas.sql` (+ índice em `transacoes.grupo_parcela_id`).

---

## Passo 8 — Investimentos

`Ativo` (RESERVA/RENDA_FIXA/RENDA_VARIAVEL) com `valorAtual` incremental. `MovimentacaoAtivo` é o
histórico único (aporte, resgate, rendimento): aporte/resgate também geram uma `Transacao` real
(dinheiro saindo/voltando de uma conta); rendimento é ganho contábil puro, sem mexer em saldo de
conta nenhuma. Evolução do patrimônio reconstruída retroativamente a partir do valor atual, sem
precisar de snapshots históricos.

Migração: `V12__investimentos.sql`.

---

## Passo 9 — Simulação de saldo diário

`GET /api/projecao?dias=&contaId=&simulacaoValor=&simulacaoData=` projeta o saldo dia a dia
assumindo que cada pendência (PENDENTE/ATRASADA) é paga no vencimento. Suporta simular uma compra
hipotética sem persistir nada — responde "cabe no meu mês?". Sem migração (cálculo puro).

---

## Princípios seguidos em todos os passos

- **Multi-tenant**: toda tabela nova tem `espaco_id` + índice; todo repository usa
  `findByIdAndEspacoId` (nunca só `findById`) — evita acesso cruzado entre espaços.
- **Migrações incrementais**: nunca editar uma migration já existente, sempre a próxima versão.
- **Reaproveitar em vez de duplicar**: transferência, fatura, dívida e meta reaproveitam a mesma
  máquina de status/pagamento/cancelamento de `Transacao` em vez de cada uma inventar a sua.
- **Validação** (Bean Validation) em todo DTO novo; operações que mexem em saldo + entidade de
  domínio ao mesmo tempo (aportar, resgatar, fechar fatura) são `@Transactional`.
