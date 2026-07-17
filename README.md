cd # Financeiro

Aplicativo de gestão financeira. Servidor multi-tenant (Spring Boot + React), com login obrigatório e dados em PostgreSQL. Hospedado no [Render](https://render.com) com banco gerenciado no [Neon](https://neon.tech).

---

## Rodando em produção

O deploy é automático via `render.yaml` (Blueprint do Render) a cada push na `main`. Não há passo manual.

---

## Para desenvolvedores

### Pré-requisitos

- Java 21+
- Maven (ou usar o bundled em `backend/.maven/`)
- Node.js 20+ (ou deixar o `frontend-maven-plugin` baixar automaticamente)
- Docker (para o Postgres local)

### Subir o Postgres local

```bash
docker compose up -d
```

Isso sobe um Postgres 16 em `localhost:5432` (usuário/senha/banco `financeiro`), com dado persistido num volume Docker.

### Rodar em modo desenvolvimento

Na primeira vez, copie o template de variáveis de ambiente do backend:

```bash
cd backend
copy .env.local.example .env.local
```

`.env.local` não vai pro git (mesmo esquema do `frontend/.env.local`) — ajuste os valores se seu Postgres local usar outras credenciais.

```bash
# Terminal 1 — backend (porta 8080), lê backend/.env.local automaticamente
cd backend
./dev.sh        # Git Bash / WSL / Linux / macOS
.\dev.ps1       # PowerShell

# Terminal 2 — frontend com hot reload (porta 5173)
cd frontend
npm install
npm run dev
```

Acesse `http://localhost:5173`. As chamadas `/api` são redirecionadas automaticamente para o backend.

Na primeira execução o Flyway aplica o schema (`V1__esquema_inicial.sql`) sozinho num banco vazio. Como login é sempre obrigatório, crie o primeiro usuário pela tela de Registro (ou `POST /api/auth/register`) — isso já cria o espaço pessoal e semeia as categorias padrão.

### Build do JAR

```bash
cd backend
.maven\apache-maven-3.9.6\bin\mvn.cmd package -DskipTests
```

O Maven builda o frontend e o embute no JAR automaticamente.
JAR gerado em: `backend/target/backend-1.0.0.jar`

Para rodar o JAR localmente, defina as mesmas env vars (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`) antes de `java -jar`.

### Publicar uma nova versão

Basta dar push na `main` — o Render builda a imagem (`Dockerfile`) e faz o deploy automaticamente.

### Estrutura do projeto

```
financeiro/
├── backend/            # Spring Boot + Java 21
├── frontend/           # React + Vite + TypeScript
├── docker-compose.yml  # Postgres local para desenvolvimento
├── Dockerfile          # Imagem usada no deploy (Render)
└── render.yaml         # Blueprint de deploy do Render
```

### Banco de dados

PostgreSQL, gerenciado via Flyway (`backend/src/main/resources/db/migration`). Em produção, o banco é o Neon; localmente, use o `docker-compose.yml` acima. Nunca edite uma migration já aplicada — sempre crie uma nova (`V7__...sql`, `V8__...sql`, etc.).
