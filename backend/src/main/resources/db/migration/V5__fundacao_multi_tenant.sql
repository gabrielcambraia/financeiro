-- Fundação multi-tenant: espaços (tenant), usuários e vínculo N:N
-- Posse do dado passa a ser por "espaço" (workspace), não por usuário direto:
-- suporta pessoa física (1 usuário = 1 espaço) e empresa (vários usuários no
-- mesmo espaço) sem re-arquitetura, e habilita planos por espaço.
--
-- Nesta PR ainda não há autenticação: todo dado existente é atribuído ao
-- espaço padrão (id=1). O SQLite não aceita NOT NULL + REFERENCES inline num
-- ADD COLUMN, então a FK de espaco_id nas tabelas de dados é garantida na
-- aplicação (ContextoEspaco), não no banco.

CREATE TABLE espacos (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    nome      TEXT NOT NULL,
    tipo      TEXT NOT NULL DEFAULT 'PESSOAL',   -- PESSOAL | EMPRESA
    plano     TEXT NOT NULL DEFAULT 'GRATUITO',  -- GRATUITO | PAGO
    criado_em TEXT NOT NULL
);

CREATE TABLE usuarios (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    email      TEXT NOT NULL UNIQUE,
    senha_hash TEXT,                 -- nullable nesta PR: sem autenticação ainda
    nome       TEXT NOT NULL,
    criado_em  TEXT NOT NULL
);

CREATE TABLE usuarios_espacos (
    usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
    espaco_id  INTEGER NOT NULL REFERENCES espacos(id),
    papel      TEXT NOT NULL DEFAULT 'DONO',     -- DONO | MEMBRO
    PRIMARY KEY (usuario_id, espaco_id)
);

-- Espaço/usuário padrão do modo desktop-local (id fixo = 1)
INSERT INTO espacos (id, nome, tipo, plano, criado_em)
VALUES (1, 'Pessoal', 'PESSOAL', 'GRATUITO', strftime('%Y-%m-%dT%H:%M:%S', 'now'));

INSERT INTO usuarios (id, email, senha_hash, nome, criado_em)
VALUES (1, 'local@financeiro.app', NULL, 'Usuário Local', strftime('%Y-%m-%dT%H:%M:%S', 'now'));

INSERT INTO usuarios_espacos (usuario_id, espaco_id, papel) VALUES (1, 1, 'DONO');

-- Coluna de tenant nas tabelas de dados; DEFAULT 1 faz o backfill de todas as
-- linhas existentes para o espaço padrão no mesmo ALTER.
ALTER TABLE contas     ADD COLUMN espaco_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE categorias ADD COLUMN espaco_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE transacoes ADD COLUMN espaco_id INTEGER NOT NULL DEFAULT 1;

CREATE INDEX idx_contas_espaco     ON contas(espaco_id);
CREATE INDEX idx_categorias_espaco ON categorias(espaco_id);
CREATE INDEX idx_transacoes_espaco ON transacoes(espaco_id);
