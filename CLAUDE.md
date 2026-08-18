# Financeiro — Contexto do Projeto

## Postura de colaboração

Antes de implementar qualquer sugestão, avaliar criticamente se ela faz sentido. Se houver problema — técnico, de segurança, de arquitetura, de consistência com o restante do projeto ou de UX — apontar diretamente e discutir antes de prosseguir. Não concordar por padrão; concordar só quando a proposta for de fato a melhor opção. Se houver alternativa melhor, propô-la com justificativa curta.

Isso vale inclusive pra soluções minhas: se um fix já implementado funciona mas não é a melhor forma arquiteturalmente (ex.: resolve o sintoma introduzindo uma segunda convenção paralela pra fazer algo que já tinha um jeito estabelecido), dizer isso proativamente em vez de deixar como está só porque já compila e passa no teste manual. Caso de referência: `TratadorGlobalExcecoes` ganhou um `@ExceptionHandler(AuthenticationException.class)` pra corrigir login quebrado, funcionou, mas era redundante com o `ResponseStatusException` que já existia — a solução final foi alinhar `ServicoAutenticacao` à convenção existente e remover o handler novo (ver "Convenção de tratamento de erros" abaixo).

## Convenção de tratamento de erros

Serviços sinalizam erro HTTP lançando `ResponseStatusException(status, mensagem)` — é o único mecanismo usado no projeto pra isso (`ServicoTokenAtualizacao`, `ServicoAutenticacao.renovar()`, conflito de e-mail em `registrar()`), e `TratadorGlobalExcecoes` já tem um `@ExceptionHandler(ResponseStatusException.class)` que cobre todos os casos.

**Não usar exceções do `org.springframework.security.*` (`BadCredentialsException`, etc.) em código de autenticação manual.** Esse projeto não usa `AuthenticationManager`/`AuthenticationProvider` do Spring Security — login é verificação de hash feita à mão em `ServicoAutenticacao`. Emprestar o nome de uma exceção do Security sem usar o mecanismo do Security que a trata (o `authenticationEntryPoint` da cadeia de filtros) faz a exceção vazar de forma inconsistente: às vezes cai no `@ExceptionHandler(Exception.class)` genérico (500 "Erro interno"), às vezes escapa pro `authenticationEntryPoint` do Spring Security e vira um 401 cru do Spring, sem nunca passar pela resposta customizada da API. Foi exatamente esse bug (login rejeitando credenciais corretas com mensagens sem sentido) que motivou documentar essa convenção.

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
│       ├── db/migration/               # Flyway (V1...V16)
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

`Conta.saldo` é um campo derivado, não editável via API: nasce igual a `saldoInicial` na criação (`ContaService.create()`) e a partir daí só é alterado por `ContaService.adjustBalance()`, chamado pelos fluxos acima e por `MetaService`/`AtivoService` (aporte/resgate). `ContaService.update()` ignora `saldo` e `saldoInicial` do payload — ambos são imutáveis após a criação; `ContaDTO.saldo` é `@JsonProperty(access = READ_ONLY)`.

## Transações fixas

Ao criar uma transação com `fixa = true`:
1. Cria a entrada do mês atual com `saldoAjustado = true` (saldo ajustado imediatamente)
2. Pré-cria 11 meses futuros com `saldoAjustado = false`
3. No startup, `AgendadorTransacaoFixa.process()` estende a janela para sempre ter 12 meses à frente

Deleção com escopo `FUTURE` apaga a partir da data selecionada.

## Padrão: links direcionados com filtro pré-aplicado

Quando um link navega para outra página já filtrada (ex.: "Ver todas" num card do Painel abrindo Transações filtradas), passe os filtros via `state` do React Router (`<Link to="/rota" state={{...}}>`, lido do lado de destino com `useLocation().state`) — **nunca** via query string (`?campo=valor`). Isso mantém a URL limpa e os filtros não ficam visíveis/editáveis pela barra de endereço nem viram algo bookmarkável.

- Filtros que já são estado global compartilhado (ex.: `mes`/`contaId` em `useLojaFiltro`, usado tanto no Painel quanto em Transações) **não precisam ser passados** — já chegam prontos na página de destino, porque é o mesmo store Zustand em toda a navegação SPA.
- Só use `state` para filtros que são estado local da página de destino (ex.: `filtroTipo` em `Transacoes.tsx`) e que, portanto, resetam a cada visita.
- Exemplo de referência: `frontend/src/components/CartaoVencimentos.tsx` (origem, `<Link state={{ tipo, dataVencimentoFim }}>`) e `frontend/src/pages/Transacoes.tsx` (destino, lê `useLocation().state`).

## Convenção de datas no banco

**A partir de V17, todas as colunas de data/hora usam `TIMESTAMP` ou `DATE` nativo do PostgreSQL.** Não use `TEXT` para datas em código novo.

- Colunas de auditoria (`criado_em`, `atualizado_em`): `TIMESTAMP NOT NULL DEFAULT NOW()`
- Datas de domínio (`data`, `data_vencimento`, `expira_em`, `vigencia_inicio`...): `TIMESTAMP` ou `DATE` sem default (obrigatório via Java)
- `data_nascimento` de PF: `TEXT` (string livre — o usuário pode digitar "1990-01" sem dia)
- Entidades Java: **não usar** `@Convert(converter = ConversorLocalDateTime.class)` em colunas novas; o Hibernate mapeia `LocalDateTime`↔`TIMESTAMP` nativamente

### Origem do padrão TEXT (V1–V16)

O projeto começou com SQLite, cujo driver JDBC (xerial) gravava `LocalDateTime` como epoch millis em vez de ISO string. Os conversores `ConversorLocalDate` e `ConversorLocalDateTime` foram criados para contornar isso. Ao migrar para PostgreSQL o padrão TEXT foi mantido por compatibilidade, mas é um legado — não deve ser propagado.

### Plano de migração das tabelas legadas (V1–V16)

Tabelas com colunas `TEXT` que deveriam ser `TIMESTAMP`/`DATE`:

| Tabela | Colunas TEXT que serão migradas |
|---|---|
| `espacos` | `criado_em` |
| `usuarios` | `criado_em` |
| `contas` | `criado_em` |
| `categorias` | `criado_em` |
| `transacoes` | `data`, `data_vencimento`, `data_pagamento`, `data_cancelamento`, `criado_em` |
| `tokens_atualizacao` | `criado_em`, `expira_em` |
| `cartoes` | `criado_em` |
| `faturas` | `data_fechamento`, `data_vencimento` |
| `itens_fatura` | `data`, `data_cancelamento` |
| `orcamentos` | `criado_em` |
| `metas` | `prazo`, `criado_em` |
| `dividas` | `data_inicio`, `data_fim`, `criado_em` |
| `ativos` | `data_compra`, `criado_em` |
| `rendimentos` | `data`, `criado_em` |

**Como executar a migração:**
1. Criar `V22__datas_para_timestamp.sql` com `ALTER TABLE ... ALTER COLUMN ... TYPE TIMESTAMP USING coluna::TIMESTAMP`
   - PostgreSQL converte ISO 8601 (`'2026-08-05T10:30:00'`) para `TIMESTAMP` nativamente no `USING`
2. Remover `@Convert(converter = ConversorLocalDateTime.class)` das entidades correspondentes
3. Testar localmente com `docker compose down -v && docker compose up -d` (banco limpo)
4. Os conversores `ConversorLocalDate`/`ConversorLocalDateTime` podem ser removidos quando não houver mais nenhum `@Convert` referenciando-os

> ⚠️ Esta migration altera colunas em tabelas com dados em produção. Testar exaustivamente antes de aplicar na `main`.

## Migrações Flyway

Sempre incrementar — nunca editar uma migration já aplicada.

**A partir de V24, versionamento por timestamp.** Migrations até V23 mantêm a numeração sequencial (`V1`...`V23`); a partir daqui, o nome do arquivo passa a ser `V{yyyyMMddHHmmss}__descricao.sql` (ex.: `V20260810153000__nexo10_assinatura_multiplo_espaco.sql`), com o número do ticket do Jira embutido na descrição quando fizer sentido para rastreabilidade.

- **Por quê:** com duas máquinas/branches trabalhando em paralelo sem estar na `main`, numeração sequencial pequena colide facilmente (duas branches criando `V24` ao mesmo tempo). Timestamp praticamente elimina a colisão sem precisar de coordenação manual — o Flyway compara os números de versão numericamente, então um timestamp (bem maior que 23) sempre ordena depois de `V23` normalmente, mesmo misturando os dois esquemas no histórico.
- Gerar o timestamp no momento de criar o arquivo (data/hora local, não precisa ser UTC nem exato ao segundo) — só precisa ser maior que o `V23` e improvável de colidir com o de outra pessoa.
- Não é necessário renumerar as migrations antigas (V1–V23) para o novo esquema.

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
| V13 | Módulos por espaço |
| V14 | Configuração de plataforma |
| V15 | Rendimento automático de ativos |
| V16 | Saldo inicial de conta (`saldo_inicial`) |
| V17 | Planos e assinaturas por espaço |
| V18 | Perfil de usuário e verificação de contato |
| V19 | Entidades (CPF/CNPJ por espaço) |
| V20 | Códigos de verificação / OTP |
| V21 | `entidade_id` nas tabelas de domínio |
| V22 | Multa por atraso em transações |
| V23 | `origem_fixa_id` e `serie_ativa` em transações fixas |

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
