CREATE TABLE centros_custo (
  id        BIGSERIAL PRIMARY KEY,
  nome      VARCHAR(255) NOT NULL,
  cor       VARCHAR(7)   NOT NULL,
  espaco_id BIGINT       NOT NULL REFERENCES espacos(id)   ON DELETE CASCADE,
  entidade_id BIGINT     NULL     REFERENCES entidades(id) ON DELETE SET NULL,
  criado_em TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_cc_espaco_entidade ON centros_custo(espaco_id, entidade_id);

ALTER TABLE transacoes   ADD COLUMN centro_custo_id BIGINT NULL REFERENCES centros_custo(id) ON DELETE SET NULL;
ALTER TABLE itens_fatura ADD COLUMN centro_custo_id BIGINT NULL REFERENCES centros_custo(id) ON DELETE SET NULL;

CREATE INDEX ix_transacoes_centro_custo   ON transacoes(centro_custo_id);
CREATE INDEX ix_itens_fatura_centro_custo ON itens_fatura(centro_custo_id);
