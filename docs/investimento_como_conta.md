# Refatoração: Investimento como Conta

## Problema atual

O aporte em um ativo cria uma `MovimentacaoAtivo` e uma transação do tipo `DESPESA` na conta de origem. Isso é semanticamente errado:

- Despesa **reduz patrimônio**; aporte apenas **realoca patrimônio** (dinheiro → ativo)
- O relatório de despesas do mês fica inflado com aportes
- O saldo real da conta de origem some sem rastro de para onde foi
- `Ativo.valorAtual` e `Conta.saldo` são campos paralelos que precisam ficar manualmente sincronizados

## Estado atual

```
Conta (CORRENTE/POUPANCA)
  └── Aporte → MovimentacaoAtivo (tipo APORTE) + Transacao (DESPESA)  ← errado
  └── Resgate → MovimentacaoAtivo (tipo RESGATE) + Transacao (RECEITA)

Ativo
  ├── valorAtual  ← mantido incrementalmente pelo service
  ├── conta_id    ← conta informativa (onde o ativo está custodiado)
  └── rendimentos automáticos → MovimentacaoAtivo (tipo RENDIMENTO)
```

## Estado alvo

```
Conta (CORRENTE)          Conta (INVESTIMENTO)  ←→  Ativo (metadados)
       │                          │
       └── TRANSFERENCIA_SAIDA ───┘ TRANSFERENCIA_ENTRADA   (aporte)
       │                          │
       └── TRANSFERENCIA_ENTRADA ─┘ TRANSFERENCIA_SAIDA     (resgate)
                                  │
                                  └── RECEITA                (rendimento)
```

- `Conta.saldo` da conta de investimento **é** o valor atual do ativo
- `Ativo.valorAtual` torna-se derivado (= `conta.saldo`) e pode ser removido no longo prazo
- `MovimentacaoAtivo` mantida para histórico, mas novos registros passam pelo fluxo de `Transacao`

---

## Banco de dados

### Migration — `V20260812130000__ativo_conta_vinculada.sql`

```sql
-- Cada ativo passa a ter uma conta própria de investimento
ALTER TABLE ativos
    ADD COLUMN conta_investimento_id BIGINT REFERENCES contas(id);

-- Registra a conta de origem padrão para aportes futuros
ALTER TABLE ativos
    ADD COLUMN conta_origem_id BIGINT REFERENCES contas(id);
```

> **Dados existentes:** um script de migração de dados (executado uma vez) deve:
> 1. Para cada `Ativo` ativo, criar uma `Conta` do tipo `INVESTIMENTO` com `saldo = ativo.valor_atual`
> 2. Preencher `conta_investimento_id` com a nova conta criada
> 3. `conta_origem_id` pode ficar nulo (usuário escolhe na hora do aporte)

---

## Backend

### `Ativo.java`

Adicionar:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "conta_investimento_id")
private Conta contaInvestimento;   // conta cujo saldo = valor do ativo

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "conta_origem_id")
private Conta contaOrigem;         // sugestão de origem para aportes
```

`valorAtual` continua existindo por ora como cache — será atualizado automaticamente via `ContaService.adjustBalance()` ao processar as transferências.

### `AtivoService.java` — mudar `aportar()` e `resgatar()`

**Antes:**
```java
// cria MovimentacaoAtivo + Transacao(DESPESA) na conta de origem
public void aportar(Long ativoId, BigDecimal valor, LocalDate data) { ... }
```

**Depois:**
```java
public void aportar(Long ativoId, BigDecimal valor, LocalDate data, Long contaOrigemId) {
    Ativo ativo = buscarValidado(ativoId);
    // delega para TransacaoService.criarTransferencia()
    transacaoService.criarTransferencia(
        contaOrigemId,
        ativo.getContaInvestimento().getId(),
        valor, data, "Aporte — " + ativo.getNome()
    );
    // sincroniza valorAtual com o novo saldo da conta
    ativo.setValorAtual(ativo.getContaInvestimento().getSaldo());
}

public void resgatar(Long ativoId, BigDecimal valor, LocalDate data, Long contaDestinoId) {
    Ativo ativo = buscarValidado(ativoId);
    transacaoService.criarTransferencia(
        ativo.getContaInvestimento().getId(),
        contaDestinoId,
        valor, data, "Resgate — " + ativo.getNome()
    );
    ativo.setValorAtual(ativo.getContaInvestimento().getSaldo());
}
```

### `AtivoService.java` — mudar `registrarRendimento()`

**Depois:**
```java
public void registrarRendimento(Long ativoId, BigDecimal valor, LocalDate data) {
    Ativo ativo = buscarValidado(ativoId);
    // rendimento = RECEITA na conta de investimento
    transacaoService.criar(TransacaoDTO.receita(
        ativo.getContaInvestimento().getId(),
        valor, data, "Rendimento — " + ativo.getNome()
    ));
    ativo.setValorAtual(ativo.getContaInvestimento().getSaldo());
}
```

### `AgendadorAtivo.java` (rendimento automático)

O `creditarRendimentoAutomatico()` já é atômico — apenas trocar a chamada interna de `ajustarSaldo(ativo, valor)` por `registrarRendimento(ativoId, valor, data)` do novo fluxo.

### `AtivoService.create()`

Ao criar um ativo, criar automaticamente a `Conta` vinculada:

```java
Conta contaInvestimento = contaService.criarParaAtivo(
    espaco, ativo.getNome(), ativo.getCor(), ativo.getIcone(),
    aporteInicial  // saldo inicial = aporte inicial, se houver
);
ativo.setContaInvestimento(contaInvestimento);
```

> A conta de investimento **não deve aparecer** na listagem padrão de contas — adicionar flag `oculta = true` na `Conta` ou filtrar por tipo na query do `ContaController`.

### Endpoints alterados — `AtivoController.java`

| Endpoint | Mudança |
|---|---|
| `PATCH /{id}/aportar` | Recebe `contaOrigemId` no body |
| `PATCH /{id}/resgatar` | Recebe `contaDestinoId` no body |
| `PATCH /{id}/rendimento` | Sem mudança de contrato externo |

---

## Frontend

### Formulário de aporte

Adicionar campo **"Conta de origem"** (select das contas do espaço, excluindo tipo INVESTIMENTO):

```tsx
<select value={contaOrigemId} onChange={...}>
  {contas.filter(c => c.tipo !== 'INVESTIMENTO').map(...)}
</select>
```

Se o ativo tiver `contaOrigem` configurada, pré-selecionar.

### Formulário de resgate

Adicionar campo **"Conta de destino"** — mesmo padrão inverso.

### Listagem de contas

Filtrar contas do tipo `INVESTIMENTO` fora da listagem principal (ou exibir em seção separada "Contas de investimento"). Definir comportamento na UI — ver ponto de decisão abaixo.

### Painel / Dashboard

O `patrimônio total` já soma todos os `conta.saldo` — com a mudança, o valor dos ativos passa a entrar automaticamente no cálculo sem lógica paralela.

---

## Pontos de decisão

| Questão | Opção A | Opção B |
|---|---|---|
| Conta de investimento visível na listagem de contas? | Sim, em seção separada — usuário vê saldo por ativo | Não — o saldo aparece só na página de Investimentos |
| `Ativo.valorAtual` após a migração | Mantém como cache sincronizado após cada operação | Deprecia — sempre ler `contaInvestimento.saldo` |
| `MovimentacaoAtivo` existente | Mantém tabela para histórico legado; novos registros só via `Transacao` | Migra histórico para `Transacao` e remove a tabela |
| Conta de origem obrigatória no aporte | Obrigatório no formulário | Opcional — se nulo, apenas atualiza saldo da conta de investimento sem gerar transferência |

**Recomendação:**
- Contas de investimento: **visíveis em seção separada** (dá mais transparência ao patrimônio)
- `valorAtual`: **manter como cache** por ora — remove a necessidade de alterar todas as queries de relatório de uma vez
- `MovimentacaoAtivo`: **manter para histórico**, parar de criar novas entradas após a migração
- Conta de origem: **obrigatória** — força o rastro completo do fluxo de caixa

---

## Riscos e cuidados

**Dados em produção:**
- A migração de dados (criar `Conta` para cada `Ativo` existente) deve rodar dentro de uma transaction; se falhar, nenhuma conta é criada
- `valorAtual` dos ativos existentes vira o `saldoInicial` da nova conta — deve bater com o histórico de movimentações

**Rendimento automático (`AgendadorAtivo`):**
- O agendador chama `creditarRendimentoAutomatico()` que é atômico (marca `rendidoAte` + ajusta saldo na mesma transaction) — manter essa atomicidade ao redirecionar para `TransacaoService`

**Relatórios de despesas:**
- Aportes que hoje aparecem como DESPESA vão sumir dos relatórios de despesa após a migração — **mudança visível para o usuário**, comunicar

---

## Ordem de implementação

1. Migration SQL (`conta_investimento_id`, `conta_origem_id` em `ativos`)
2. Script de dados: criar `Conta(INVESTIMENTO)` para cada ativo existente
3. Backend: `Ativo.java` + `AtivoService.create()` (nova conta ao criar ativo)
4. Backend: `AtivoService.aportar()` e `resgatar()` → `TransacaoService.criarTransferencia()`
5. Backend: `AtivoService.registrarRendimento()` → `TransacaoService.criar(RECEITA)`
6. Backend: `AgendadorAtivo` — adaptar `creditarRendimentoAutomatico()`
7. Testes: `AtivoTest` — verificar que aporte gera TRANSFERENCIA, não DESPESA
8. Frontend: adicionar campo "Conta de origem/destino" nos formulários de aporte e resgate
9. Frontend: filtrar contas INVESTIMENTO da listagem principal
10. Frontend: exibir contas INVESTIMENTO em seção separada (opcional — pode ir em PR separado)
