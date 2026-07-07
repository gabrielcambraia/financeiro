-- Recria o schema em português (tabelas, colunas e valores de enum)

CREATE TABLE contas (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    nome   TEXT NOT NULL,
    tipo   TEXT NOT NULL DEFAULT 'CORRENTE',
    saldo  REAL NOT NULL DEFAULT 0,
    cor    TEXT NOT NULL DEFAULT '#6366f1',
    icone  TEXT NOT NULL DEFAULT 'wallet'
);

INSERT INTO contas (id, nome, tipo, saldo, cor, icone)
SELECT id, name,
       CASE type WHEN 'CHECKING' THEN 'CORRENTE' WHEN 'SAVINGS' THEN 'POUPANCA'
                 WHEN 'WALLET' THEN 'CARTEIRA' WHEN 'INVESTMENT' THEN 'INVESTIMENTO' ELSE type END,
       balance, color, icon
FROM accounts;

CREATE TABLE categorias (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    nome  TEXT NOT NULL,
    tipo  TEXT NOT NULL,
    cor   TEXT NOT NULL DEFAULT '#6366f1',
    icone TEXT NOT NULL DEFAULT 'tag'
);

INSERT INTO categorias (id, nome, tipo, cor, icone)
SELECT id, name,
       CASE type WHEN 'INCOME' THEN 'RECEITA' WHEN 'EXPENSE' THEN 'DESPESA' ELSE type END,
       color, icon
FROM categories;

CREATE TABLE transacoes (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    conta_id           INTEGER NOT NULL REFERENCES contas(id),
    categoria_id       INTEGER REFERENCES categorias(id),
    tipo               TEXT NOT NULL,
    tipo_pagamento     TEXT NOT NULL DEFAULT 'DEBITO',
    valor              REAL NOT NULL,
    descricao          TEXT,
    data               TEXT NOT NULL,
    fixa               INTEGER NOT NULL DEFAULT 0,
    saldo_ajustado     INTEGER NOT NULL DEFAULT 1,
    total_parcelas     INTEGER,
    numero_parcela     INTEGER,
    grupo_parcela_id   TEXT,
    criado_em          TEXT NOT NULL
);

INSERT INTO transacoes (id, conta_id, categoria_id, tipo, tipo_pagamento, valor, descricao, data,
                         fixa, saldo_ajustado, total_parcelas, numero_parcela, grupo_parcela_id, criado_em)
SELECT id, account_id, category_id,
       CASE type WHEN 'INCOME' THEN 'RECEITA' WHEN 'EXPENSE' THEN 'DESPESA' ELSE type END,
       CASE payment_type WHEN 'DEBIT' THEN 'DEBITO' WHEN 'CREDIT' THEN 'CREDITO' ELSE payment_type END,
       amount, description, date, is_fixed, balance_adjusted,
       installment_total, installment_number, installment_group_id, created_at
FROM transactions;

DROP TABLE transactions;
DROP TABLE categories;
DROP TABLE accounts;
