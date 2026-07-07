# Secretaria Financeira via WhatsApp — Roadmap de Implementação

## Visão Geral

Transformar o Financeiro num assistente financeiro pessoal acessível pelo WhatsApp. O usuário manda uma mensagem de texto ou áudio ("gastei 50 reais no mercado") e o sistema registra a transação automaticamente, confirma e mantém o histórico financeiro atualizado em tempo real.

---

## Branding do Produto

**Nome sugerido:** Fin — sua secretaria financeira  
**Persona do bot:** Assistente financeiro proativo, direto, sem enrolação. Fala em português informal mas profissional.  
**Proposta de valor:** Você não precisa abrir nenhum app. Só manda uma mensagem como mandaria para um assistente humano.

### Exemplos de interação

```
Usuário: "gastei 47 reais no mercado hoje"
Fin:     "Anotado! 🛒 Mercado — R$ 47,00 debitado da conta Principal. Saldo atual: R$ 1.243,00"

Usuário: [áudio de 8s: "recebi meu salário de três mil reais"]
Fin:     "Salário de R$ 3.000,00 registrado na conta Principal! 💰 Saldo: R$ 4.243,00"

Usuário: "quanto gastei essa semana?"
Fin:     "Essa semana você gastou R$ 312,50 em 7 transações. Maior gasto: Mercado (R$ 47,00)."

Usuário: "cria uma conta de luz fixa de 120 reais todo mês"
Fin:     "Criado! Conta de luz de R$ 120,00 agendada todo dia 10. Próximo vencimento: 10/06."
```

---

## Arquitetura do Sistema

### Visão atual (local)
```
[Usuário] → [start.bat] → [Spring Boot + SQLite] → [React UI]
```

### Visão futura (cloud)
```
[WhatsApp] → [Meta API] → [Webhook Server] → [AI Agent] → [API Core] → [PostgreSQL]
                                                                ↓
                                                         [React UI Web]
                                                                ↓
                                                         [Usuário no browser]
```

### Componentes novos

| Componente | Responsabilidade |
|---|---|
| **Webhook Service** | Recebe eventos do WhatsApp, valida assinatura, enfileira |
| **AI Agent** | Interpreta texto/áudio, extrai intent + entidades financeiras |
| **Message Queue** | Desacopla webhook do processamento (resiliência) |
| **Auth Service** | Vincula número de telefone a usuário/conta |
| **Notification Service** | Envia respostas e alertas proativos via WPP |

---

## Stack Tecnológica Recomendada

### Backend (evolutivo — mantém Java/Spring Boot)

| Camada | Tecnologia | Motivo |
|---|---|---|
| Core API | Spring Boot 3.3 + Java 21 | Já existe, não reescreve |
| Banco | PostgreSQL (Supabase) | SQLite não escala multi-usuário |
| Migrações | Flyway | Já em uso |
| Queue | Redis + Spring Events (simples) ou RabbitMQ | Garante entrega das mensagens |
| Auth | Spring Security + JWT + OAuth2 | Padrão sólido |
| Webhook | Controller dedicado no mesmo Spring Boot | Simplicidade |

### AI Agent

| Componente | Tecnologia | Motivo |
|---|---|---|
| LLM | Claude API (claude-haiku-4-5) | Barato, rápido, preciso em PT-BR |
| Transcrição de áudio | Whisper API (OpenAI) ou AssemblyAI | Melhor qualidade em PT-BR |
| Orquestração | Function Calling (tool use) do Claude | Extração estruturada de dados |

### Infraestrutura

| Serviço | Escolha | Plano gratuito |
|---|---|---|
| Servidor | Railway ou Render | Sim (512MB RAM) |
| Banco PostgreSQL | Supabase | Sim (500MB) |
| Cache/Queue | Upstash Redis | Sim (10k req/dia) |
| Domínio | Cloudflare | Sim |
| WhatsApp | Meta for Developers | Gratuito até 1k mensagens/mês |

**Custo estimado inicial:** R$ 0 a R$ 50/mês para uso pessoal/beta

---

## Fases de Implementação

---

### FASE 1 — Preparar o core para cloud
**Estimativa:** 2–3 semanas  
**Pré-requisito para tudo mais**

#### 1.1 Multi-tenancy básico
- Adicionar tabela `users` com `id`, `email`, `phone_number`, `wpp_verified`
- Adicionar `user_id` (FK) em `accounts`, `transactions`, `categories`
- Toda query filtrada por `user_id` (row-level security)
- Migration Flyway V4

#### 1.2 Autenticação
- JWT stateless (Spring Security)
- Login via email/senha na UI web
- Endpoint `POST /api/auth/login` e `POST /api/auth/register`
- Middleware que extrai `userId` do token e injeta no contexto

#### 1.3 Migrar banco para PostgreSQL
- Trocar dependência SQLite → PostgreSQL no `pom.xml`
- Ajustar `application.properties` para ler `DATABASE_URL` da env
- Manter perfil `local` com SQLite para desenvolvimento offline
- Testar todas as migrations no Postgres

#### 1.4 Deploy inicial sem WPP
- Criar conta no Railway
- Configurar variáveis de ambiente (`DATABASE_URL`, `JWT_SECRET`, etc.)
- CI/CD: GitHub Actions já existente, adaptar para deploy no Railway
- HTTPS automático via Railway (obrigatório para webhook do WPP)

---

### FASE 2 — Integração WhatsApp
**Estimativa:** 1–2 semanas  
**Dependência:** Fase 1 completa + conta Meta for Developers

#### 2.1 Configurar Meta for Developers
```
1. Criar app no developers.facebook.com
2. Adicionar produto "WhatsApp"
3. Configurar número de teste
4. Registrar webhook URL: https://seudominio.com/api/wpp/webhook
5. Assinar eventos: messages, message_status
6. Salvar VERIFY_TOKEN e ACCESS_TOKEN nas envs
```

#### 2.2 Webhook Controller
```java
// POST /api/wpp/webhook — recebe mensagens
// GET  /api/wpp/webhook — verificação do Meta (desafio)
@RestController
@RequestMapping("/api/wpp")
public class WhatsAppWebhookController {
    // Valida assinatura HMAC-SHA256 do header X-Hub-Signature-256
    // Enfileira evento para processamento assíncrono
    // Retorna 200 imediatamente (Meta exige resposta em < 5s)
}
```

#### 2.3 Vinculação de número
- Fluxo: usuário cadastra o número na UI → recebe código de 6 dígitos via WPP → confirma → número vinculado
- Tabela `wpp_verifications(user_id, phone, code, expires_at, verified)`

#### 2.4 Envio de mensagens (WhatsApp Cloud API)
```java
// Serviço para enviar respostas
public class WhatsAppSenderService {
    // POST https://graph.facebook.com/v18.0/{phone_id}/messages
    // Autenticação: Bearer {ACCESS_TOKEN}
    // Body: { to, type: "text", text: { body } }
}
```

---

### FASE 3 — AI Agent
**Estimativa:** 2–3 semanas  
**O coração da feature**

#### 3.1 Transcrição de áudio
```
Fluxo para mensagens de voz:
1. Webhook recebe media_id do áudio
2. Baixa arquivo via Graph API
3. Envia para Whisper/AssemblyAI
4. Recebe texto transcrito
5. Segue para interpretação igual texto normal
```

#### 3.2 Interpretação com Claude (Tool Use)
O agent recebe o texto e decide qual "ferramenta" usar:

```json
// Tools disponíveis para o Claude
[
  {
    "name": "create_transaction",
    "description": "Cria uma transação financeira",
    "input_schema": {
      "amount": "number",
      "type": "INCOME | EXPENSE",
      "description": "string",
      "category": "string",
      "date": "ISO date (default hoje)",
      "account": "string (default Principal)",
      "fixed": "boolean"
    }
  },
  {
    "name": "get_balance",
    "description": "Retorna saldo atual do usuário"
  },
  {
    "name": "get_summary",
    "description": "Resumo de gastos por período",
    "input_schema": {
      "period": "week | month | year"
    }
  },
  {
    "name": "list_transactions",
    "description": "Lista transações recentes ou por filtro"
  }
]
```

#### 3.3 Prompt do sistema (contexto do usuário)
```
Você é Fin, assistente financeira pessoal do {nome}.
Contas disponíveis: {lista de contas com saldos}
Categorias disponíveis: {lista de categorias}
Data de hoje: {data}

Interprete a mensagem e chame a ferramenta adequada.
Responda sempre em português informal, máximo 2 linhas.
Se não entender, peça esclarecimento com um exemplo.
```

#### 3.4 Fluxo completo de processamento
```
Mensagem recebida
    ↓
Identificar usuário pelo número (ou pedir cadastro)
    ↓
Se áudio → transcrever → texto
    ↓
Chamar Claude com contexto do usuário + tools
    ↓
Claude retorna tool_use com parâmetros
    ↓
Executar a tool (criar transação, buscar saldo, etc.)
    ↓
Montar resposta amigável
    ↓
Enviar via WhatsApp API
```

---

### FASE 4 — Segurança e Produção
**Estimativa:** 1 semana  
**Antes de abrir para outros usuários**

#### 4.1 Segurança do Webhook
- Validar `X-Hub-Signature-256` em TODA requisição do Meta
- Rate limiting por número de telefone (evitar spam/flood)
- Idempotência: salvar `message_id` processado para evitar duplicatas

#### 4.2 Segurança da API
- HTTPS obrigatório (Railway fornece automaticamente)
- JWT com expiração curta (15min access + 7d refresh)
- Limitar tentativas de login (brute force)
- CORS configurado para domínio específico
- Sanitizar todos os inputs antes de passar para o LLM (prompt injection)

#### 4.3 Proteção contra Prompt Injection
```java
// Nunca passar input do usuário diretamente no system prompt
// Sempre usar a estrutura: system (fixo) + user (mensagem isolada)
// Validar que o Claude só chama tools definidas, nunca retorna JSON livre
```

#### 4.4 Gestão de segredos
```
Variáveis de ambiente obrigatórias (nunca no código):
- DATABASE_URL
- JWT_SECRET (mínimo 256 bits)
- WPP_ACCESS_TOKEN
- WPP_VERIFY_TOKEN
- WPP_APP_SECRET (para validar HMAC)
- ANTHROPIC_API_KEY
- WHISPER_API_KEY
```

---

### FASE 5 — Features avançadas (pós-lançamento)
**Nice to have**

- **Relatório mensal automático:** todo dia 1, resumo do mês anterior via WPP
- **Alertas de orçamento:** "você já usou 80% do orçamento de alimentação"
- **Foto de nota fiscal:** usuário manda foto, Claude extrai valor e categoria (Vision API)
- **Multi-conta:** suporte a contas separadas por mensagem ("debita da conta PJ")
- **Exportar para planilha:** "me manda o extrato de abril em Excel"
- **Interface web mobile-first:** complementar ao WPP para visualização

---

## Melhor Forma de Rodar na Internet

### Recomendação: Railway (caminho mais simples)

**Por que Railway:**
- Deploy via `git push` (integra com GitHub Actions já existente)
- PostgreSQL incluído no plano gratuito
- HTTPS automático com domínio `*.railway.app`
- Variáveis de ambiente no dashboard
- Logs e métricas sem configuração
- Escala automaticamente

**Configuração mínima:**
```toml
# railway.toml na raiz
[build]
builder = "nixpacks"
buildCommand = "cd backend && .maven/apache-maven-3.9.6/bin/mvn package -DskipTests"

[deploy]
startCommand = "java -jar backend/target/backend-1.0.0.jar"
healthcheckPath = "/api/health"
```

### Alternativa: VPS própria (mais controle, mais trabalho)
```
- DigitalOcean Droplet $6/mês ou Oracle Cloud Free Tier
- Docker Compose: Spring Boot + PostgreSQL + Redis + Nginx
- Certbot para SSL
- Mais controle, mais manutenção
```

### Comparativo

| Critério | Railway | VPS |
|---|---|---|
| Configuração | 30 min | 3–4 horas |
| Custo inicial | Grátis | Grátis (Oracle) ou $6/mês |
| HTTPS | Automático | Manual (Certbot) |
| Escalabilidade | Automática | Manual |
| Lock-in | Médio | Nenhum |
| Recomendado para | MVP / validação | Produção madura |

---

## Sequência de Execução Recomendada

```
Semana 1–3:   [FASE 1] Multi-tenancy + Auth + PostgreSQL + Deploy Railway
Semana 4:     [FASE 2] WhatsApp webhook + vinculação de número
Semana 5–7:   [FASE 3] AI Agent (Claude + Whisper)
Semana 8:     [FASE 4] Segurança + testes + hardening
Semana 9+:    [FASE 5] Features avançadas conforme demanda
```

---

## Riscos e Mitigações

| Risco | Probabilidade | Mitigação |
|---|---|---|
| Meta bloquear número por spam | Média | Rate limiting + template messages aprovados |
| Custo de API disparar | Baixa | Limite de requisições por usuário/dia + alertas de billing |
| Claude interpretar errado | Média | Sempre confirmar antes de criar (configurável por usuário) |
| Usuário mandar dado sensível | Alta | Política clara de privacidade + não logar conteúdo de mensagens |
| SQLite → Postgres quebrar queries | Baixa | Testar migration completa em staging antes |

---

## Referências e Documentação

- Meta WhatsApp Cloud API: `developers.facebook.com/docs/whatsapp/cloud-api`
- Claude Tool Use: `docs.anthropic.com/en/docs/tool-use`
- Whisper API: `platform.openai.com/docs/guides/speech-to-text`
- Railway Deploy: `docs.railway.app`
- Spring Boot + PostgreSQL: `docs.spring.io`
