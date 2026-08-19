ALTER TABLE itens_fatura ADD COLUMN entidade_id BIGINT NULL REFERENCES entidades(id) ON DELETE SET NULL;
CREATE INDEX ix_itens_fatura_espaco_entidade ON itens_fatura(espaco_id, entidade_id);
