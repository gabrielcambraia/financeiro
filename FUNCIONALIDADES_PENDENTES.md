# Funcionalidades Pendentes — Backend

Registro das features sinalizadas no frontend (protótipos) que **não existem** ainda no backend.
Cada item indica o endpoint sugerido, os parâmetros e o comportamento esperado.

---

## 1. Exportar Lançamentos

**Tela:** Lançamentos (`/transacoes`)
**Botão no front:** "Exportar"

### Endpoint sugerido
```
GET /api/transacoes/exportar
```

### Parâmetros (mesmos filtros do GET /api/transacoes)
| Parâmetro             | Tipo       | Descrição |
|-----------------------|------------|-----------|
| `month`               | String     | Obrigatório — "YYYY-MM" |
| `tipo`                | TipoTransacao | Opcional — RECEITA / DESPESA / TRANSFERENCIA |
| `contaId`             | Long       | Opcional |
| `categoriaId`         | Long       | Opcional |
| `dataVencimentoInicio`| LocalDate  | Opcional |
| `dataVencimentoFim`   | LocalDate  | Opcional |
| `formato`             | String     | Opcional — `csv` (padrão) ou `pdf` |

### Comportamento esperado
- Retorna `Content-Disposition: attachment; filename="lancamentos-YYYY-MM.csv"` (ou `.pdf`)
- CSV: cabeçalho com colunas `data,descricao,tipo,categoria,conta,valor,status`
- PDF: tabela estilizada com totais de receitas/despesas/resultado no rodapé
- Respeitar `espaco_id` do usuário autenticado

### Dependências
- Spring `ResponseEntity<byte[]>` com `MediaType.APPLICATION_OCTET_STREAM`
- Para CSV: `opencsv` ou escrita manual com `StringBuilder`
- Para PDF: `iText` ou `Apache PDFBox` (avaliar licença)

---

## 2. Importar Lançamentos

**Tela:** Lançamentos (`/transacoes`)
**Botão no front:** "Importar"

### Endpoint sugerido
```
POST /api/transacoes/importar
Content-Type: multipart/form-data
```

### Parâmetros
| Parâmetro  | Tipo            | Descrição |
|------------|-----------------|-----------|
| `arquivo`  | MultipartFile   | Arquivo CSV ou OFX |
| `contaId`  | Long            | Conta destino das transações importadas |
| `formato`  | String          | `csv` ou `ofx` |

### Comportamento esperado
- Lê o arquivo linha a linha, valida campos obrigatórios (`data`, `descricao`, `valor`)
- Cria transações com `saldoAjustado` calculado conforme regra existente (`data <= hoje`)
- Retorna JSON com:
  ```json
  {
    "importadas": 42,
    "ignoradas": 3,
    "erros": [{ "linha": 7, "motivo": "data inválida" }]
  }
  ```
- Em caso de erro parcial, não faz rollback dos registros já inseridos (ou usar `@Transactional` + rollback total — decidir)
- Duplicatas: verificar por `(data, descricao, valor, contaId)` e ignorar se já existe

### Formato CSV esperado
```
data,descricao,valor,tipo,categoria,conta
2026-08-01,Salário,8000.00,RECEITA,Salário,
2026-08-02,Mercado,-342.80,DESPESA,Alimentação,
```

### Formato OFX (Open Financial Exchange)
- Parsing básico do `<STMTTRN>` — data (`DTPOSTED`), valor (`TRNAMT`), descrição (`MEMO`/`NAME`)
- Associar automaticamente à `contaId` passada no parâmetro

### Dependências
- `spring-web` (já presente) para `MultipartFile`
- Para OFX: `ofx4j` ou parsing manual com DOM/SAX

---

## 3. Exportar Fatura do Cartão

**Tela:** Detalhe do Cartão (`/cartoes/:id`)
**Botão no front:** "Exportar"

### Endpoint sugerido
```
GET /api/faturas/{faturaId}/exportar
```
ou por cartão + mês:
```
GET /api/faturas/exportar?cartaoId={id}&month=YYYY-MM&formato=csv|pdf
```

### Comportamento esperado
- Exporta todos os `ItemFatura` daquela fatura
- CSV: `data,descricao,categoria,valor,parcela`
- PDF: cabeçalho com nome do cartão, período, total; tabela de itens agrupada por data; rodapé com total da fatura
- Resposta com `Content-Disposition: attachment; filename="fatura-CARTAO-YYYY-MM.csv"`

---

## 4. Importar Itens de Fatura

**Tela:** Detalhe do Cartão (`/cartoes/:id`)
**Botão no front:** "Importar"

### Endpoint sugerido
```
POST /api/itens-fatura/importar
Content-Type: multipart/form-data
```

### Parâmetros
| Parâmetro  | Tipo          | Descrição |
|------------|---------------|-----------|
| `arquivo`  | MultipartFile | CSV com itens da fatura |
| `cartaoId` | Long          | Cartão de destino |
| `month`    | String        | Competência — "YYYY-MM" |

### Comportamento esperado
- Formato CSV esperado: `data,descricao,valor,categoria,parcelas`
- Cria `ItemFatura` para cada linha válida
- Retorna resumo de importadas / ignoradas / erros (mesmo padrão do endpoint de transações)

---

## 5. Filtros adicionais em GET /api/transacoes (melhoria)

**Contexto:** O endpoint atual aceita apenas 6 parâmetros. Para melhorar a UX do filtro no front:

| Parâmetro      | Tipo     | Descrição |
|----------------|----------|-----------|
| `status`       | StatusTransacao | PENDENTE / PAGA / ATRASADA / CANCELADA |
| `fixa`         | boolean  | Somente transações fixas (`true`) ou variáveis (`false`) |
| `descricao`    | String   | Busca por substring na descrição (ILIKE `%descricao%`) |
| `page`         | int      | Paginação — número da página (0-based) |
| `size`         | int      | Paginação — itens por página (padrão 20) |
| `sort`         | String   | Ordenação — ex: `data,desc` ou `valor,asc` |

### Implementação sugerida
- Migrar de `List<TransacaoDTO>` para `Page<TransacaoDTO>` usando Spring Data `Pageable`
- Adicionar `@RequestParam(required = false)` para os novos filtros
- No `TransacaoRepository`, usar `Specification<Transacao>` (JPA Criteria) ou query JPQL com predicados condicionais
- Retornar `RespostaPaginada<TransacaoDTO>` (DTO já existe em `RespostaPaginada.java`)

---

---

## 6. Comparativo com mês anterior nos KPIs do Dashboard

**Status: dados já existem no backend — nenhuma alteração necessária.**

O `GET /api/painel` já retorna `tendenciaMensal` com os últimos 6 meses (`receita` + `despesa` por mês). O frontend calcula a variação assim:

```ts
// tendenciaMensal ordenado do mais antigo para o mais recente
const mesAtual   = tendenciaMensal[tendenciaMensal.length - 1];  // mês selecionado
const mesAnterior = tendenciaMensal[tendenciaMensal.length - 2]; // mês -1

const variacaoReceita = calcVariacao(mesAtual.receita, mesAnterior.receita);
const variacaoDespesa = calcVariacao(mesAtual.despesa, mesAnterior.despesa);

function calcVariacao(atual: number, anterior: number): number {
  if (anterior === 0) return 0;
  return ((atual - anterior) / anterior) * 100;
}
```

**O que falta só no frontend:**
- Implementar o cálculo acima ao consumir `PainelDTO.tendenciaMensal`
- Exibir o `%` e seta ↑↓ nos cards KPI, que hoje estão mockados no protótipo

**Campos do `PainelDTO` relevantes (já existem):**
- `totalReceitas`, `totalDespesas`, `saldoLiquido` — valores do mês selecionado
- `tendenciaMensal[n].receita` / `.despesa` — séries históricas para calcular variação
- `saldosContas[].saldo` — somar no front para obter "Saldo Total consolidado"

---

## Prioridade sugerida

| # | Feature | Impacto | Esforço |
|---|---------|---------|---------|
| 1 | Filtros adicionais em GET /api/transacoes | Alto | Médio |
| 2 | Exportar lançamentos (CSV) | Alto | Baixo |
| 3 | Exportar fatura (CSV) | Alto | Baixo |
| 4 | Importar lançamentos (CSV) | Médio | Médio |
| 5 | Exportar lançamentos (PDF) | Médio | Alto |
| 6 | Importar itens de fatura (CSV) | Médio | Médio |
| 7 | Exportar fatura (PDF) | Baixo | Alto |
| 8 | Importar lançamentos (OFX) | Baixo | Alto |
