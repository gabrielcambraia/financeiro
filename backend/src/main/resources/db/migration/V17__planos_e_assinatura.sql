-- Planos da plataforma (Individual, Família, Empresa) com limite de entidades
CREATE TABLE planos (
    id               BIGSERIAL PRIMARY KEY,
    codigo           TEXT      NOT NULL UNIQUE,
    nome             TEXT      NOT NULL,
    limite_entidades INTEGER   NOT NULL,
    ativo            BOOLEAN   NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO planos (codigo, nome, limite_entidades, ativo, criado_em) VALUES
    ('INDIVIDUAL', 'Individual', 1, TRUE, '2026-08-04 00:00:00'),
    ('FAMILIA',    'Família',    1, TRUE, '2026-08-04 00:00:00'),
    ('EMPRESA',    'Empresa',    5, TRUE, '2026-08-04 00:00:00');

-- Assinatura: um espaço tem exatamente um plano ativo
CREATE TABLE assinaturas (
    id              BIGSERIAL PRIMARY KEY,
    espaco_id       BIGINT    NOT NULL UNIQUE REFERENCES espacos(id) ON DELETE CASCADE,
    plano_id        BIGINT    NOT NULL REFERENCES planos(id),
    status          TEXT      NOT NULL,
    vigencia_inicio TIMESTAMP NOT NULL,
    vigencia_fim    TIMESTAMP,
    criada_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizada_em   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_assinaturas_plano ON assinaturas (plano_id);

-- Todos os espaços existentes ganham assinatura INDIVIDUAL
INSERT INTO assinaturas (espaco_id, plano_id, status, vigencia_inicio, criada_em, atualizada_em)
SELECT
    e.id,
    (SELECT id FROM planos WHERE codigo = 'INDIVIDUAL'),
    'ATIVA',
    e.criado_em::TIMESTAMP,
    '2026-08-04 00:00:00',
    '2026-08-04 00:00:00'
FROM espacos e;
