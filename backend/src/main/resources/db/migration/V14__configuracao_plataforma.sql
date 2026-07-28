-- Configuração global da plataforma (singleton, linha única id=1): hoje só
-- guarda a logo usada como favicon do navegador e no lugar do texto
-- "Financeiro" na barra lateral. Gerenciada só por administradores.
CREATE TABLE configuracao_plataforma (
    id        BIGINT PRIMARY KEY,
    logo      BYTEA,
    logo_tipo TEXT
);

INSERT INTO configuracao_plataforma (id) VALUES (1);
