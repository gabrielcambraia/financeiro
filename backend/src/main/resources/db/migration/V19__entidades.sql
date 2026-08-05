-- Entidades (PF ou PJ) vinculadas a um espaço — com CPF/CNPJ cifrado
CREATE TABLE entidades (
    id                 BIGSERIAL PRIMARY KEY,
    espaco_id          BIGINT    NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    tipo_pessoa        TEXT      NOT NULL,
    nome               TEXT      NOT NULL,
    nome_fantasia      TEXT,
    documento_cifrado  BYTEA     NOT NULL,
    documento_hash     TEXT      NOT NULL,
    inscricao_estadual TEXT,
    data_nascimento    TEXT,
    email              TEXT,
    telefone           TEXT,
    cep                TEXT,
    logradouro         TEXT,
    numero             TEXT,
    complemento        TEXT,
    bairro             TEXT,
    cidade             TEXT,
    uf                 TEXT,
    criado_em          TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- documento_hash único por espaço (não globalmente, pois a mesma PF pode ser sócia em dois espaços distintos)
CREATE UNIQUE INDEX ix_entidades_doc_espaco ON entidades (espaco_id, documento_hash);
CREATE INDEX ix_entidades_espaco ON entidades (espaco_id);
