# Financeiro — Contexto do Projeto

## Convenção de idioma

Todo código novo (métodos, variáveis, classes, DTOs, comentários, mensagens) deve ser escrito em português. Não usar nomes em inglês para identificadores novos, mesmo que o restante do ecossistema (Spring, React) use termos em inglês.

## O que é

Aplicativo de gestão financeira, servidor **multi-tenant** (Spring Boot + React) com **login obrigatório**. Cada usuário pertence a um ou mais **Espaços** (workspaces), e todo dado de domínio (contas, categorias, transações, cartões, metas, dívidas, investimentos...) é escopado por `espaco_id`. Hospedado na nuvem: [Render](https://render.com) (deploy automático via `render.yaml` a cada push na `main`) com banco gerenciado no [Neon](https://neon.tech).

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 3.3 + Java 21 |
| Frontend | React 19 + Vite + TypeScript + Tailwind CSS |
| Banco | PostgreSQL (Neon em produção; Postgres 16 via `docker-compose.yml` localmente) |
| Auth | JWT (access token curto) + refresh token; login sempre obrigatório |
| Migrações | Flyway |
| Deploy | Render, runtime Docker (`Dockerfile`), blueprint `render.yaml` |
| Build | Maven 3.9 (bundled em `backend/.maven/`) |

## Multi-tenancy e segurança

- **Espaço** (`Espaco`) é o workspace: todas as tabelas de domínio carregam `espaco_id` e toda query de repository/service deve ser filtrada pelo espaço do usuário autenticado.
- `Usuario` ↔ `Espaco` via `UsuarioEspaco`, com papel por espaço (`PapelUsuario`: `DONO`/`MEMBRO`).
- `NivelAcesso` é um papel **global** da plataforma (`USUARIO`/`ADMIN`), independente de espaço — hoje só usado para gerenciar o catálogo de bancos.
- Autenticação via `ServicoJwt` (access token) + `ServicoTokenAtualizacao` (refresh token). Filtros em `seguranca/` cobrem autenticação JWT, verificação de Origin (defesa contra CSRF), rate limit de login e troca de senha obrigatória.

## Arquitetura de deploy

O frontend é buildado pelo `frontend-maven-plugin` durante `mvn package` e embutido em `backend/src/main/resources/static/`. O resultado é um JAR único que serve o React como arquivos estáticos e expõe a API em `/api/**`. O `ConfiguracaoSpa.java` faz fallback para `index.html` em rotas não-API para suportar o React Router.

Em produção, esse mesmo JAR roda dentro da imagem Docker (`Dockerfile`, buildada e publicada automaticamente pelo Render a cada push na `main`), conectando ao Postgres remoto via variáveis de ambiente (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`, `CORS_ORIGENS_PERMITIDAS`).

## Estrutura

```
financeiro/
├── backend/                    # Spring Boot
│   ├── .maven/                 # Maven portátil (não commitar dependências)
│   ├── .node/                  # Node.js baixado pelo frontend-maven-plugin (gitignored)
│   ├── .env.local(.example)    # Env vars locais (datasource, JWT_SECRET); não vai pro git
│   ├── dev.sh / dev.ps1        # Sobe o backend em dev lendo .env.local
│   ├── src/main/java/com/financeiro/
│   │   ├── config/
│   │   │   ├── ConfiguracaoSpa.java        # Fallback SPA para React Router
│   │   │   ├── ConfiguracaoWeb.java        # CORS para dev mode
│   │   │   └── ConfiguracaoSeguranca.java  # Security filter chain, JWT, CORS/Origin
│   │   ├── seguranca/                   # JWT, refresh token, filtros (auth/origem/rate-limit), autorização por espaço/admin
│   │   ├── controller/                  # REST controllers (todos em /api/**)
│   │   ├── dto/                         # DTOs de request/response
│   │   ├── entity/                      # Entidades JPA (Espaco, Usuario, Conta, Transacao, Cartao, Divida, Meta, Ativo...)
│   │   ├── repository/                  # Spring Data JPA
│   │   ├── scheduler/
│   │   │   ├── AgendadorTransacaoFixa.java  # Ver seção "Lógica de saldo"
│   │   │   └── AgendadorFatura.java         # Fecha fatura de cartão no dia de fechamento
│   │   └── service/
│   └── src/main/resources/
│       ├── application.properties
│       ├── db/migration/               # Flyway (V1...V12)
│       └── static/                     # Output do build do frontend (gitignored)
├── frontend/                   # React + Vite
│   └── src/
│       ├── api/                # Clientes HTTP (axios, baseURL='/api')
│       ├── components/
│       ├── pages/
│       ├── store/              # Zustand
│       └── types/index.ts
├── docker-compose.yml           # Postgres 16 local para desenvolvimento
├── Dockerfile                   # Imagem usada no deploy (Render)
├── render.yaml                  # Blueprint de deploy do Render
└── .github/workflows/
    └── testes.yml                # CI: roda os testes do backend (sem build do frontend)
```

## Lógica de saldo (`saldoAjustado`)

Campo booleano (`saldo_ajustado`) em `Transacao`. Regra central do sistema:

- `true` → saldo já foi ajustado na conta (transação "realizada")
- `false` → transação existe mas ainda não afetou o saldo (data futura)

**Onde é definido:**
- `TransacaoService.create()`: `true` se `data <= hoje`, `false` se data futura. Transações fixas pré-criam 11 meses futuros com `false`.
- `TransacaoService.update()`: recalcula com base na nova data e reverte/aplica saldo conforme necessário.
- `TransacaoService.delete()`: só reverte saldo se `saldoAjustado = true`.
- `AgendadorTransacaoFixa.process()`: roda no startup (`onStartup`, via `ApplicationReadyEvent`) e no dia 1° de cada mês (`onFirstOfMonth`, `@Scheduled(cron = "0 5 0 1 * *")`). Ajusta saldo de todas as transações com `false` e `data <= hoje`. Estende janela de 12 meses à frente para transações fixas.

**Por que não usar o scheduler como única fonte:** o app pode ficar dias sem receber tráfego. O startup garante que meses perdidos sejam processados ao subir novamente.

## Transações fixas

Ao criar uma transação com `fixa = true`:
1. Cria a entrada do mês atual com `saldoAjustado = true` (saldo ajustado imediatamente)
2. Pré-cria 11 meses futuros com `saldoAjustado = false`
3. No startup, `AgendadorTransacaoFixa.process()` estende a janela para sempre ter 12 meses à frente

Deleção com escopo `FUTURE` apaga a partir da data selecionada.

## Migrações Flyway

Sempre incrementar — nunca editar uma migration já aplicada.

| Versão | O que faz |
|---|---|
| V1 | Esquema inicial (espaços, usuários, contas, categorias, transações) |
| V2 | Tokens de atualização (refresh token) |
| V3 | Datas e status de transação |
| V4 | Transferência entre contas |
| V5 | Cartão de crédito / fatura |
| V6 | Banco, contas e cartões |
| V7 | Catálogo de bancos (admin) |
| V8 | Enum de nível de acesso |
| V9 | Orçamento por categoria |
| V10 | Metas financeiras |
| V11 | Dívidas |
| V12 | Investimentos (ativos) |

## Dev mode (frontend separado)

Pré-requisitos: Java 21+, Maven (ou o bundled em `backend/.maven/`), Node.js 20+, Docker (para o Postgres local).

```bash
# Subir o Postgres local (uma vez)
docker compose up -d

# Primeira vez: copiar o template de env vars do backend
cd backend
copy .env.local.example .env.local   # ajustar credenciais se necessário

# Terminal 1 — backend (porta 8080), lê backend/.env.local automaticamente
cd backend
./dev.sh        # Git Bash / WSL / Linux / macOS
.\dev.ps1       # PowerShell

# Terminal 2 — frontend com HMR (porta 5173)
cd frontend
npm install
npm run dev   # Proxy /api → localhost:8080 configurado no vite.config.ts
```

Na primeira execução o Flyway aplica o schema num banco vazio. Como login é sempre obrigatório, crie o primeiro usuário pela tela de Registro (ou `POST /api/auth/register`) — isso já cria o espaço pessoal e semeia as categorias padrão.

## Build do JAR

```bash
cd backend
.maven/apache-maven-3.9.6/bin/mvn package -DskipTests
# JAR gerado em: backend/target/backend-1.0.0.jar
```

O Maven builda o frontend e o embute no JAR automaticamente (via `frontend-maven-plugin`). Para rodar o JAR localmente, defina as mesmas env vars (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`) antes de `java -jar`.

## Publicar em produção

Basta dar push na `main` — o Render builda a imagem (`Dockerfile`) e faz o deploy automaticamente. Não há passo manual nem tags de release.
