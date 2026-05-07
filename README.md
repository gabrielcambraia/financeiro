# Financeiro

Aplicativo de gestão financeira pessoal. Roda localmente no Windows sem precisar de internet ou conta em serviço externo.

---

## Para usuários finais

1. Acesse a página de [Releases](../../releases) do repositório
2. Baixe o arquivo `financeiro-vX.X.X.zip` da versão mais recente
3. Extraia em uma pasta de sua preferência (ex: `C:\financeiro\`)
4. Execute o `start.bat`

Na primeira execução, se Java não estiver instalado, o instalador cuida disso automaticamente.

### Atualizar

1. Baixe o ZIP da nova versão
2. Extraia **na mesma pasta** onde já está instalado
3. Execute o `start.bat` normalmente

> Seus dados ficam no arquivo `financeiro.db`, que não é apagado na atualização.

---

## Para desenvolvedores

### Pré-requisitos

- Java 21+
- Maven (ou usar o bundled em `backend/.maven/`)
- Node.js 20+ (ou deixar o `frontend-maven-plugin` baixar automaticamente)

### Rodar em modo desenvolvimento

```bash
# Terminal 1 — backend (porta 8080)
cd backend
.maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run

# Terminal 2 — frontend com hot reload (porta 5173)
cd frontend
npm install
npm run dev
```

Acesse `http://localhost:5173`. As chamadas `/api` são redirecionadas automaticamente para o backend.

### Build do JAR

```bash
cd backend
.maven\apache-maven-3.9.6\bin\mvn.cmd package -DskipTests
```

O Maven builda o frontend e o embute no JAR automaticamente.
JAR gerado em: `backend/target/backend-1.0.0.jar`

### Publicar uma nova versão

1. Faça commit de todas as alterações e suba para o `main`
2. Crie e suba uma tag com a versão:

```bash
git tag v1.0.1
git push origin main --tags
```

O GitHub Actions vai automaticamente:
- Buildar o JAR (com frontend embutido)
- Criar o arquivo `financeiro-v1.0.1.zip`
- Publicar um Release no GitHub com o ZIP e as instruções de instalação

Acompanhe o progresso em: `Actions` → `Release`

### Estrutura do projeto

```
financeiro/
├── backend/          # Spring Boot + Java 21
├── frontend/         # React + Vite + TypeScript
├── start.bat         # Inicialização para desenvolvimento
├── start-release.bat # Inicialização para usuário final (incluído no ZIP)
└── .github/
    └── workflows/
        └── release.yml   # Pipeline de build e publicação
```

### Banco de dados

O banco SQLite (`financeiro.db`) é criado automaticamente na primeira execução, na mesma pasta do JAR. As migrações são gerenciadas pelo Flyway — ao atualizar o JAR, novas migrações são aplicadas automaticamente sem perder dados.
