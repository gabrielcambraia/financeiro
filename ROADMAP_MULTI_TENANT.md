# Migração para multi-tenant (servidor na internet) — Roadmap

## Decisão de arquitetura

Uma única instância multi-tenant, com posse do dado por **espaço (workspace)**, não por usuário direto:

```
usuarios          (id, email, senha_hash, nome, criado_em)
espacos           (id, nome, tipo[PESSOAL|EMPRESA], plano[GRATUITO|PAGO], criado_em)
usuarios_espacos  (usuario_id, espaco_id, papel[DONO|MEMBRO])   -- vínculo N:N

contas.espaco_id / categorias.espaco_id / transacoes.espaco_id
```

Por quê:
- **Pessoa física hoje**: 1 usuário ↔ 1 espaço.
- **Empresa depois**: vários usuários no mesmo espaço, sem re-arquitetura — só adicionar linhas em `usuarios_espacos`.
- **Um código só, dois públicos**: plano gratuito/pago vive no espaço (`espacos.plano`), não em projetos separados.
- É o que o `ROADMAP_WPP_AGENT.md` (Fase 1) já assumia e o que o futuro agente WhatsApp exige (roteia número → usuário → espaço).

Sequência definida: **PR 1** (fundação de dados) → **PR 2** (autenticação JWT) → **PR 3** (PostgreSQL + deploy Railway). O modo desktop-local (`.bat`, sem login) continua funcionando em todas as etapas.

---

## PR 1 — Fundação de dados multi-tenant ✅ concluído

**O que mudou:**
- Migration `V5__fundacao_multi_tenant.sql`: cria `espacos`, `usuarios`, `usuarios_espacos`; adiciona `espaco_id` (com backfill automático para o espaço padrão `id=1`, "Pessoal") em `contas`, `categorias`, `transacoes`. Sem FK de banco nessas 3 colunas (limitação do SQLite com `ALTER TABLE` + `NOT NULL` + `REFERENCES`) — posse garantida na aplicação.
- Entidades novas: `Espaco`, `Usuario`, `UsuarioEspaco`/`UsuarioEspacoId`. `espacoId` escalar em `Conta`, `Categoria`, `Transacao`.
- Seam `ContextoEspaco` / `ContextoEspacoPadrao`: hoje resolve sempre para o espaço `1` (via `financeiro.espaco-padrao=1`). Injetado só nos services — controllers e DTOs não mudaram.
- Repositórios: métodos `...AndEspacoId(...)` scopeados em `ContaRepository`, `CategoriaRepository`, `TransacaoRepository`. O agendador (`AgendadorTransacaoFixa`) mantém queries globais de propósito (roda em background, sem contexto de request) mas propaga o `espacoId` de cada linha de origem.
- Fechados 3 vazamentos cross-espaço que existiam antes desta mudança: `ContaService.delete` usava `deleteById` cego; `TransacaoService` anexava conta/categoria por id sem checar espaço; `PainelService.buildSaldosContas` usava `findAll()` (mostraria saldos de todos os espaços).

**Como foi verificado** (sem suíte de testes automatizados — o projeto ainda não tem `spring-boot-starter-test`):
- App rodada contra uma **cópia** do `financeiro.db` real → migration aplicou sem erro, todos os endpoints (`/contas`, `/categorias`, `/transacoes`, `/painel`) retornaram os mesmos dados de antes, agendador processou normalmente.
- Criado um "espaço 2" via SQL direto com conta/categoria próprias → confirmado que ficam **invisíveis** ao espaço padrão e, ao alternar `financeiro.espaco-padrao=2`, só os dados do espaço 2 aparecem.
- `financeiro.db` do repositório **não foi tocado** — a migration roda sozinha na próxima vez que a app subir normalmente. Backup pré-migration guardado à parte.

**Pendências deixadas para depois:**
- Sem testes automatizados (`IsolamentoEspacoTest` etc.) — projeto não tem infra de testes ainda.
- Seeder de categorias padrão por espaço novo (`SemeadorCategoriasPadrao.semear(espacoId)`) — só é necessário quando existir criação de espaços via registro (PR 2).

---

## PR 2 — Autenticação ✅ concluído

**Objetivo:** o app vira multiusuário de verdade — cada usuário loga e acessa seu(s) espaço(s).

**O que mudou:**
- Dependências novas: `spring-boot-starter-security` + `jjwt` (api/impl/jackson 0.12.6).
- Dois perfis Spring coexistindo no mesmo JAR, sem quebrar o `.bat`:
  - **default/desktop-local** (nenhum profile ativo): `ConfiguracaoSeguranca.cadeiaSegurancaLocal` libera tudo (`permitAll`), `ContextoEspacoPadrao` (`@Profile("!nuvem")`) continua resolvendo sempre o espaço 1. `ServicoJwt`, `ServicoAutenticacao`, `FiltroAutenticacaoJwt`, `AutenticacaoController` (register/login) nem são instanciados — todos `@Profile("nuvem")`, então o modo local não exige `JWT_SECRET`.
  - **nuvem** (`--spring.profiles.active=nuvem`): `cadeiaSegurancaNuvem` exige JWT em `/api/**` (exceto `/api/auth/**`), `ContextoEspacoSeguranca` (`@Profile("nuvem")`) lê `usuarioId`/`espacoId` do `SecurityContextHolder` via `FiltroAutenticacaoJwt`. `GET /api/auth/config` existe nos dois perfis (endpoint de descoberta, sem profile) e devolve `{requerAutenticacao}` para o frontend saber se deve exigir login.
- Fluxo de registro (`POST /api/auth/register`, `ServicoAutenticacao.registrar`, `@Transactional`): cria `Usuario` (senha com BCrypt) + `Espaco` pessoal + `UsuarioEspaco(DONO)` + semeia as 14 categorias padrão (`SemeadorCategoriasPadrao`, mesma lista de `V2__seed_categories.sql`/`V4__traducao_pt_br.sql`, replicada em código) — tudo atômico.
- Fluxo de login (`POST /api/auth/login`): valida e-mail/senha (BCrypt; usuário com `senha_hash` nulo — caso do usuário local id=1 — nunca autentica), resolve o espaço vinculado, gera JWT com claims `usuarioId`/`espacoId`.
- Nenhuma migration nova: `senha_hash` já existia (nullable) desde `V5`.
- Frontend: `store/lojaAutenticacao.ts` (Zustand + localStorage), `api/autenticacao.ts`, interceptors em `api/cliente.ts` (injeta `Authorization: Bearer`, trata 401 limpando sessão e redirecionando), páginas `Login.tsx`/`Registro.tsx`, `components/RotaProtegida.tsx` (consulta `/auth/config`; só exige sessão se `requerAutenticacao`), botão "Sair" em `Estrutura.tsx`.

**Como foi verificado** (sem suíte de testes automatizados):
- Build completo (`mvn package`) e `tsc -b`/`vite build` do frontend sem erros.
- Perfil default rodando numa pasta isolada: `/api/contas` e `/` respondem 200 sem token, `/api/auth/config` devolve `requerAutenticacao:false` — comportamento idêntico ao pré-PR2.
- Perfil `nuvem` rodando numa pasta isolada com `JWT_SECRET` de teste: `/api/contas` sem token → 401; `POST /api/auth/register` cria usuário/espaço novo e devolve token; com o token, `/api/categorias` do espaço novo já tem as 14 categorias e `/api/contas` vem vazio (isolado do espaço 1); `POST /api/auth/login` com a senha certa funciona, com senha errada → 401; registro com e-mail duplicado → 409; `/` e `/login` (fallback SPA) continuam servindo `index.html` (200) mesmo com a cadeia de segurança ativa.

**Pendências deixadas para depois:**
- Sem testes automatizados (mesmo motivo do PR1 — projeto ainda não tem infra de testes).
- Troca de espaço ativo para usuários com múltiplos vínculos (`UsuarioEspaco`) — hoje o login sempre usa o primeiro/único espaço; relevante quando existir onboarding de empresas (vários usuários por espaço).
- CORS de produção e `JWT_SECRET` via env real ficam para o PR 3 (deploy).

## PR 2.1 — Membros do espaço e autoria de transações ✅ concluído

**Objetivo:** um DONO consegue dar acesso ao seu espaço a outras pessoas (ex.: cônjuge, sócio), e todo lançamento passa a registrar quem o criou.

**O que mudou:**
- Migration `V6__autoria_transacao_e_senha_temporaria.sql`: `transacoes.usuario_id` (backfill `DEFAULT 1`, mesmo critério da `V5`) e `usuarios.precisa_trocar_senha` (`DEFAULT 0`). Sem FK inline, mesma limitação de sempre do SQLite.
- Novo seam `ContextoUsuario`/`ContextoUsuarioPadrao`/`ContextoUsuarioSeguranca`, espelhando exatamente `ContextoEspaco` — resolve "quem está autenticado agora" sem acoplar `TransacaoService` ao perfil ativo.
- `TransacaoService` grava `usuarioId` (autor) em toda criação (`buildTransacao`) e **preserva** o autor original em `update()` — edição não rouba a autoria. `AgendadorTransacaoFixa.extendTo()` propaga `usuarioId` da transação de origem, do mesmo jeito que já propagava `espacoId`.
- `POST /api/espacos/membros` (`ServicoMembroEspaco`, `MembroEspacoController`, `@Profile("nuvem")`): só o DONO do espaço ativo (`ContextoEspaco` + `ContextoUsuario` + `UsuarioEspacoRepository`) pode chamar — outro papel ou espaço errado dá 403. E-mail já cadastrado → 409 (não há vínculo de usuário existente a outro espaço, é sempre um `Usuario` novo). Senão cria o usuário com senha temporária (`GeradorSenhaTemporaria`, `SecureRandom`, 12 chars sem caracteres ambíguos) e vincula como `MEMBRO` — a senha em texto puro só aparece nessa única resposta, nunca mais persiste em claro.
- Senha temporária vira claim `precisaTrocarSenha` no JWT (`UsuarioAutenticado`, `ServicoJwt`). `FiltroTrocaSenhaObrigatoria` bloqueia qualquer `/api/**` (exceto `/api/auth/login`, `/api/auth/config`, `/api/auth/trocar-senha`) com `403 {"codigo":"SENHA_TEMPORARIA"}` enquanto a flag estiver ativa — bloqueio real no backend, não só sugestão de UI.
- `POST /api/auth/trocar-senha` (`ServicoAutenticacao.trocarSenha`): valida a senha atual, grava a nova, zera a flag e devolve token novo (o antigo ficaria com a claim desatualizada).
- Frontend: `pages/Membros.tsx` (visível só para DONO — o backend também garante isso) mostra a senha temporária uma única vez com botão de copiar; `pages/TrocarSenha.tsx` + interceptor do axios detectando o 403 `SENHA_TEMPORARIA` e redirecionando; `RotaProtegida` também redireciona com base na flag da sessão.

**Como foi verificado** (sem suíte de testes automatizados):
- Fluxo completo via curl numa instância `nuvem` isolada: DONO registra → adiciona membro → membro loga com a senha temporária → é barrado em `/api/contas` com 403 `SENHA_TEMPORARIA` → troca a senha → passa a acessar normalmente → transação criada por ele grava `usuarioId` do próprio membro (não do DONO).
- Casos de erro: e-mail duplicado → 409; membro (não-DONO) tentando adicionar outro membro → 403; requisição sem token → 401; encoding UTF-8 da mensagem de erro do filtro conferido.
- Modo desktop-local re-testado após a migration: `/api/contas` sem token continua 200, transação criada localmente grava `usuarioId=1` (usuário local), nada quebrou.

**Pendências deixadas para depois:**
- Sem testes automatizados (mesmo motivo das PRs anteriores).
- Não há tela para o DONO listar/remover membros existentes, nem trocar o papel de alguém — só adicionar.
- `criadoPorNome` no `TransacaoDTO` (exibir o nome do autor na UI de lançamentos) não foi implementado, só o `usuarioId` cru.

## PR 3 — PostgreSQL + deploy ✅ concluído

**Objetivo:** sair do SQLite local e publicar num servidor real. Decisão tomada nesta PR: **abandonar de vez o modo desktop-local** — o app passa a ser servidor-only, com autenticação sempre obrigatória.

**O que mudou:**
- `pom.xml`: saem `sqlite-jdbc` e `hibernate-community-dialects`; entram `org.postgresql:postgresql` e `flyway-database-postgresql` (exigido pelo Flyway 10 do Spring Boot 3.3 para reconhecer o dialeto Postgres).
- Migrations `V1`–`V6` (SQLite) apagadas e substituídas por um baseline único, `V1__esquema_inicial.sql`, já em sintaxe Postgres: `id BIGINT GENERATED BY DEFAULT AS IDENTITY`, dinheiro em `NUMERIC(19,2)` (era `REAL`), booleans em `BOOLEAN` (eram `INTEGER 0/1`), datas mantidas como `text` (os conversores JPA `ConversorLocalDate`/`ConversorLocalDateTime` já serializam como string ISO — continuam funcionando sem mudança), e agora com **FKs reais** de `espaco_id`/`usuario_id` (o SQLite não permitia `NOT NULL + REFERENCES` inline num `ADD COLUMN`).
- Modo desktop-local removido por completo: `start.bat`, `start-release.bat`, `backend/start-backend.bat`, `.github/workflows/release.yml`, os beans `ContextoEspacoPadrao`/`ContextoUsuarioPadrao` e `application-nuvem.properties` foram apagados; os 9 componentes que tinham `@Profile("nuvem")` viraram incondicionais; `ConfiguracaoSeguranca` ficou com uma única `SecurityFilterChain`; `GET /api/auth/config` sempre retorna `requerAutenticacao: true`.
- `application.properties`: datasource 100% via env (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`), `server.port=${PORT:8080}` (a maioria dos PaaS injeta `$PORT`).
- Deploy: `Dockerfile` multi-stage (build com Maven+Node via `frontend-maven-plugin`, runtime `eclipse-temurin:21-jre`) e `render.yaml` (Blueprint do [Render](https://render.com), hospedagem escolhida por ter tier gratuito real sem cartão de crédito). Postgres gerenciado gratuito e persistente via [Neon](https://neon.tech) (o Postgres free do próprio Render expira em ~30 dias). CORS não mudou — o SPA é servido same-origin pelo próprio JAR em produção, então as origens `localhost` do dev seguem inofensivas.
- `scripts/exportar_sqlite_para_postgres.py`: migra os dados de um `financeiro.db` existente para o Postgres novo (`INSERT`s em ordem de FK, conversão de booleans, reset de identities ao final).

**Como foi verificado** (sem suíte de testes automatizados):
- Build completo (`mvn package`) sem erros após a troca de driver.
- Postgres 16 local via Docker: Flyway aplicou o baseline num banco vazio sem erro. Fluxo completo via curl: registro (usuário + espaço + 14 categorias) → sem token 401 → login (senha errada 401, certa 200) → registro duplicado 409 → criação de conta e transação (saldo ajustado corretamente, `usuarioId` de autoria gravado) → `/api/painel` com agregações por data/categoria → convite de membro com senha temporária → membro bloqueado com `403 SENHA_TEMPORARIA` até trocar a senha → membro não-DONO tentando convidar outro → 403.

**Pendências deixadas para depois:**
- Sem testes automatizados (mesmo motivo das PRs anteriores).
- Deploy real no Render + Neon e migração dos dados de produção (`scripts/exportar_sqlite_para_postgres.py`) ainda não executados — só validados localmente contra Postgres.
- `README.md` não foi atualizado (ainda descreve o modo desktop-local via `.bat`).

Depois disso, o roadmap segue com a integração WhatsApp descrita em `ROADMAP_WPP_AGENT.md` (Fases 2–5), que já pressupõe exatamente este modelo multi-tenant por espaço.
