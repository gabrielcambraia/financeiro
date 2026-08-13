# Centro de Custo

## Motivação

Usuários com atividades mistas (ex.: médico com despesas pessoais e do consultório) precisam separar o fluxo financeiro sem abrir contas bancárias ou CNPJs distintos. O **Centro de Custo** é uma dimensão de classificação opcional nas transações — ortogonal a conta e categoria — que permite filtrar e consolidar relatórios por contexto (Casa, Consultório, Obra, etc.).

```
Espaço
  └── Centro de Custo   ← novo nível (opcional na transação)
        └── Categoria   ← continua existindo normalmente
```

---

## Banco de dados

### Migration — `V20260812120000__centro_custo.sql`

```sql
CREATE TABLE centros_custo (
    id         BIGSERIAL PRIMARY KEY,
    espaco_id  BIGINT      NOT NULL REFERENCES espacos(id),
    nome       VARCHAR(80) NOT NULL,
    ativo      BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_centros_custo_espaco ON centros_custo(espaco_id);

ALTER TABLE transacoes
    ADD COLUMN centro_custo_id BIGINT REFERENCES centros_custo(id);

ALTER TABLE itens_fatura
    ADD COLUMN centro_custo_id BIGINT REFERENCES centros_custo(id);
```

**Regras:**
- `centro_custo_id` é sempre nullable — não quebra transações existentes.
- Sem `ON DELETE CASCADE`: ao desativar um centro de custo, as transações vinculadas ficam órfãs de forma controlada (campo vira `null` ou mantém o id para histórico — decidir na implementação).
- `ativo = false` é soft delete — não remove do histórico.

---

## Backend

### Entidade — `CentroCusto.java`

```java
@Entity
@Table(name = "centros_custo")
public class CentroCusto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "espaco_id", nullable = false)
    private Espaco espaco;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
```

### DTO — `CentroCustoDTO.java`

```java
public record CentroCustoDTO(
        Long id,
        String nome,
        boolean ativo
) {}
```

### Repository — `CentroCustoRepository.java`

```java
public interface CentroCustoRepository extends JpaRepository<CentroCusto, Long> {
    List<CentroCusto> findByEspacoIdOrderByNome(Long espacoId);
    List<CentroCusto> findByEspacoIdAndAtivoTrueOrderByNome(Long espacoId);
}
```

### Service — `CentroCustoService.java`

Métodos:
- `listar(Long espacoId)` — retorna apenas ativos
- `criar(Long espacoId, String nome)` — valida nome único no espaço
- `atualizar(Long id, Long espacoId, String nome)` — verifica propriedade
- `desativar(Long id, Long espacoId)` — soft delete

### Controller — `CentroCustoController.java`

```
GET    /api/centros-custo          → listar (ativos)
POST   /api/centros-custo          → criar
PUT    /api/centros-custo/{id}     → renomear
DELETE /api/centros-custo/{id}     → desativar (soft delete)
```

### Alterações em `Transacao` / `TransacaoDTO`

```java
// Entidade
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "centro_custo_id")
private CentroCusto centroCusto;

// DTO — campo opcional
Long centroCustoId;
```

Mesma adição em `ItemFatura` / `ItemFaturaDTO`.

`TransacaoService.create()` e `update()`: se `centroCustoId` vier preenchido, buscar o `CentroCusto`, validar que pertence ao espaço do usuário e associar.

---

## Frontend

### Gerenciamento (nova página ou seção em Configurações)

- Listagem de centros de custo do espaço
- Criar / renomear / desativar
- Indicação visual de centros inativos (mantidos para histórico)

### Formulário de transação

- Campo `<select>` opcional: "Centro de custo"
- Opções: centros ativos + "Nenhum"
- Ao selecionar espaço diferente, limpar o campo

### Formulário de item de fatura

- Mesmo campo `<select>` opcional

### Filtros

- Adicionar "Centro de custo" ao painel de filtros de `Transacoes.tsx`
- Filtro passado via `state` do React Router quando navegar por link (seguir convenção do projeto)

### Relatório / Painel

- Card ou seção "Por centro de custo": total de receitas e despesas por centro no mês selecionado
- Usar os mesmos filtros globais (`mes`, `contaId`) já existentes no `useLojaFiltro`

---

## Pontos de decisão

| Questão | Opção A | Opção B |
|---|---|---|
| Ao desativar um centro, as transações vinculadas ficam como? | Mantém `centro_custo_id` (histórico preservado, centro aparece como "inativo" nos relatórios) | `SET NULL` via trigger — perde rastreabilidade |
| Centro de custo aparece no filtro global (`useLojaFiltro`)? | Sim — persistido no store junto com `mes`/`contaId` | Não — filtro local por página |
| Limite de centros por espaço? | Sem limite | Limitar por plano (ex.: 3 no gratuito) |

**Recomendação:** Opção A em todos os três casos — preserva histórico, mantém consistência com o padrão de filtros existente e deixa espaço para monetização futura.

---

## Ordem de implementação

1. Migration SQL
2. Entidade + Repository + Service + Controller (backend)
3. Testes: `CentroCustoTest` (CRUD + segurança de espaço)
4. Atualizar `TransacaoDTO` / `TransacaoService` + `ItemFaturaDTO`
5. Frontend: página de gerenciamento
6. Frontend: campo no formulário de transação e item de fatura
7. Frontend: filtro em `Transacoes.tsx`
8. Frontend: card de relatório no Painel
