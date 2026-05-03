CREATE TABLE IF NOT EXISTS accounts (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name    TEXT NOT NULL,
    type    TEXT NOT NULL DEFAULT 'CHECKING',
    balance REAL NOT NULL DEFAULT 0,
    color   TEXT NOT NULL DEFAULT '#6366f1',
    icon    TEXT NOT NULL DEFAULT 'wallet'
);

CREATE TABLE IF NOT EXISTS categories (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    name  TEXT NOT NULL,
    type  TEXT NOT NULL,
    color TEXT NOT NULL DEFAULT '#6366f1',
    icon  TEXT NOT NULL DEFAULT 'tag'
);

CREATE TABLE IF NOT EXISTS transactions (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id           INTEGER NOT NULL REFERENCES accounts(id),
    category_id          INTEGER REFERENCES categories(id),
    type                 TEXT NOT NULL,
    payment_type         TEXT NOT NULL DEFAULT 'DEBIT',
    amount               REAL NOT NULL,
    description          TEXT,
    date                 TEXT NOT NULL,
    is_fixed             INTEGER NOT NULL DEFAULT 0,
    installment_total    INTEGER,
    installment_number   INTEGER,
    installment_group_id TEXT,
    created_at           TEXT NOT NULL
);
