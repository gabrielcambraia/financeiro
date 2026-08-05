-- Códigos de verificação de e-mail/telefone e OTP de login
CREATE TABLE codigos_verificacao (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT    REFERENCES usuarios(id) ON DELETE CASCADE,
    destinatario TEXT      NOT NULL,
    canal        TEXT      NOT NULL,
    proposito    TEXT      NOT NULL,
    codigo_hash  TEXT      NOT NULL,
    criado_em    TIMESTAMP NOT NULL DEFAULT NOW(),
    expira_em    TIMESTAMP NOT NULL,
    usado_em     TIMESTAMP,
    tentativas   SMALLINT  NOT NULL DEFAULT 0,
    ip_origem    TEXT
);

CREATE INDEX ix_cod_verif_dest_proposito ON codigos_verificacao (destinatario, proposito, criado_em);
