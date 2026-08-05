-- Adiciona entidade_id (NULL = global do espaço) em todas as tabelas de domínio.
-- ON DELETE SET NULL: se a entidade for excluída, os registros voltam ao "pool global".
ALTER TABLE contas       ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE cartoes      ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE faturas      ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE transacoes   ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE categorias   ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE metas        ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE dividas      ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE ativos       ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
ALTER TABLE orcamentos   ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;

CREATE INDEX ix_contas_espaco_entidade      ON contas(espaco_id, entidade_id);
CREATE INDEX ix_cartoes_espaco_entidade     ON cartoes(espaco_id, entidade_id);
CREATE INDEX ix_faturas_espaco_entidade     ON faturas(espaco_id, entidade_id);
CREATE INDEX ix_transacoes_espaco_entidade  ON transacoes(espaco_id, entidade_id);
CREATE INDEX ix_categorias_espaco_entidade  ON categorias(espaco_id, entidade_id);
CREATE INDEX ix_metas_espaco_entidade       ON metas(espaco_id, entidade_id);
CREATE INDEX ix_dividas_espaco_entidade     ON dividas(espaco_id, entidade_id);
CREATE INDEX ix_ativos_espaco_entidade      ON ativos(espaco_id, entidade_id);
CREATE INDEX ix_orcamentos_espaco_entidade  ON orcamentos(espaco_id, entidade_id);
