# Financeiro — Contexto do Projeto

## O que é

Aplicativo de gestão financeira pessoal desktop-local. Roda inteiramente na máquina do usuário via um arquivo `.bat` — sem servidor externo, sem conta, sem internet necessária para uso.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 3.3 + Java 21 |
| Frontend | React 19 + Vite + TypeScript + Tailwind CSS |
| Banco | SQLite (arquivo `financeiro.db` na mesma pasta do JAR) |
| Migrações | Flyway |
| Build | Maven 3.9 (bundled em `backend/.maven/`) |

## Arquitetura de distribuição

O frontend é buildado pelo `frontend-maven-plugin` durante `mvn package` e embutido em `backend/src/main/resources/static/`. O resultado é um JAR único que serve o React como arquivos estáticos e expõe a API em `/api/**`. O `SpaConfig.java` faz fallback para `index.html` em rotas não-API para suportar o React Router.

## Estrutura

```
financeiro/
├── backend/                    # Spring Boot
│   ├── .maven/                 # Maven portátil (não commitar dependências)
│   ├── .node/                  # Node.js baixado pelo frontend-maven-plugin (gitignored)
│   ├── src/main/java/com/financeiro/
│   │   ├── config/
│   │   │   ├── SpaConfig.java          # Fallback SPA para React Router
│   │   │   └── WebConfig.java          # CORS para dev mode
│   │   ├── controller/                 # REST controllers (todos em /api/**)
│   │   ├── dto/                        # DTOs de request/response
│   │   ├── entity/                     # Entidades JPA
│   │   ├── repository/                 # Spring Data JPA
│   │   ├── scheduler/
│   │   │   └── FixedTransactionScheduler.java  # Ver seção abaixo
│   │   └── service/
│   └── src/main/resources/
│       ├── application.properties
│       ├── db/migration/               # Flyway (V1, V2, V3...)
│       └── static/                     # Output do build do frontend (gitignored)
├── frontend/                   # React + Vite
│   └── src/
│       ├── api/                # Clientes HTTP (axios, baseURL='/api')
│       ├── components/
│       ├── pages/
│       ├── store/              # Zustand
│       └── types/index.ts
├── start.bat                   # Dev/build: verifica Java, compila se necessário, sobe servidor
├── start-release.bat           # Distribuição: só verifica Java e roda o JAR
└── .github/workflows/
    └── release.yml             # CI: build + ZIP + GitHub Release ao fazer push de tag
```

## Lógica de saldo (`balance_adjusted`)

Campo booleano em `Transaction`. Regra central do sistema:

- `true` → saldo já foi ajustado na conta (transação "realizada")
- `false` → transação existe mas ainda não afetou o saldo (data futura)

**Onde é definido:**
- `TransactionService.create()`: `true` se `date <= hoje`, `false` se data futura. Transações fixas pré-criam 11 meses futuros com `false`.
- `TransactionService.update()`: recalcula com base na nova data e reverte/aplica saldo conforme necessário.
- `TransactionService.delete()`: só reverte saldo se `balanceAdjusted = true`.
- `FixedTransactionScheduler.process()`: roda no startup (`@EventListener(ApplicationReadyEvent)`) e no dia 1° de cada mês (`@Scheduled`). Ajusta saldo de todas as transações com `false` e `date <= hoje`. Estende janela de 12 meses à frente para transações fixas.

**Por que não usar o scheduler como única fonte:** o app pode ficar dias desligado. O startup garante que meses perdidos sejam processados ao religar.

## Transações fixas

Ao criar uma transação com `fixed = true`:
1. Cria a entrada do mês atual com `balanceAdjusted = true` (saldo ajustado imediatamente)
2. Pré-cria 11 meses futuros com `balanceAdjusted = false`
3. No startup, `process()` estende a janela para sempre ter 12 meses à frente

Deleção com scope `FUTURE` apaga a partir da data selecionada.

## Migrações Flyway

Sempre incrementar — nunca editar uma migration existente.

| Versão | O que faz |
|---|---|
| V1 | Schema inicial (accounts, categories, transactions) |
| V2 | Seed de categorias padrão |
| V3 | Coluna `balance_adjusted` (default 1 para dados existentes) |

## Dev mode (frontend separado)

```bash
# Terminal 1 — backend
cd backend
.maven/apache-maven-3.9.6/bin/mvn spring-boot:run

# Terminal 2 — frontend com HMR
cd frontend
npm install
npm run dev   # Proxy /api → localhost:8080 configurado no vite.config.ts
```

## Build do JAR de distribuição

```bash
cd backend
.maven/apache-maven-3.9.6/bin/mvn package -DskipTests
# JAR gerado em: backend/target/backend-1.0.0.jar
```

O Maven mata e rebuilda o frontend automaticamente via `frontend-maven-plugin`.

## Publicar release

```bash
git tag v1.0.1
git push origin main --tags
```

O GitHub Actions builda, cria o ZIP e publica o Release automaticamente.
