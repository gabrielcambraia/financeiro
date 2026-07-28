-- Módulos opcionais habilitados por espaço (opt-in). Hoje existe só um
-- módulo de exemplo (WHATSAPP_IA); nenhum endpoint valida isso ainda — é
-- só a base de dados para a tela de admin marcar quais espaços têm acesso
-- a cada módulo conforme forem sendo adicionados.
CREATE TABLE espaco_modulos (
    espaco_id BIGINT NOT NULL REFERENCES espacos(id),
    modulo    TEXT NOT NULL,
    PRIMARY KEY (espaco_id, modulo)
);
