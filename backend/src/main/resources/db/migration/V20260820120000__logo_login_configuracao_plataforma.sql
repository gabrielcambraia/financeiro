-- Segundo slot de logo em configuracao_plataforma: banner exibido na tela de
-- login, independente da logo usada na barra lateral/favicon (coluna `logo`).
ALTER TABLE configuracao_plataforma
    ADD COLUMN logo_login      BYTEA,
    ADD COLUMN logo_login_tipo TEXT;
