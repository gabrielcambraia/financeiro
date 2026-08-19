-- Tabela de recorrências — entidade pai que gera lançamentos mensais via job.
-- Substitui o mecanismo fixa+origemFixaId que pré-criava 12 meses.
CREATE TABLE recorrencias (
    id               BIGSERIAL       PRIMARY KEY,
    espaco_id        BIGINT          NOT NULL REFERENCES espacos(id)       ON DELETE CASCADE,
    usuario_id       BIGINT          NOT NULL REFERENCES usuarios(id)      ON DELETE CASCADE,
    entidade_id      BIGINT          NULL     REFERENCES entidades(id)     ON DELETE SET NULL,
    tipo             VARCHAR(30)     NOT NULL,
    tipo_pagamento   VARCHAR(30)     NOT NULL,
    conta_id         BIGINT          NULL     REFERENCES contas(id)        ON DELETE SET NULL,
    cartao_id        BIGINT          NULL     REFERENCES cartoes(id)       ON DELETE SET NULL,
    categoria_id     BIGINT          NULL     REFERENCES categorias(id)    ON DELETE SET NULL,
    centro_custo_id  BIGINT          NULL     REFERENCES centros_custo(id) ON DELETE SET NULL,
    valor            NUMERIC(19, 4)  NOT NULL,
    descricao        VARCHAR(500)    NULL,
    dia_competencia  SMALLINT        NOT NULL CHECK (dia_competencia BETWEEN 1 AND 31),
    dia_vencimento   SMALLINT        NULL     CHECK (dia_vencimento  BETWEEN 1 AND 31),
    debito_automatico BOOLEAN        NOT NULL DEFAULT FALSE,
    ativa            BOOLEAN         NOT NULL DEFAULT TRUE,
    data_inicio      DATE            NOT NULL,
    data_fim         DATE            NULL,
    criado_em        TIMESTAMP       NOT NULL DEFAULT NOW(),
    atualizado_em    TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rec_pagamento CHECK (
        (tipo_pagamento = 'DEBITO'  AND conta_id  IS NOT NULL AND cartao_id IS NULL) OR
        (tipo_pagamento = 'CREDITO' AND cartao_id IS NOT NULL AND conta_id  IS NULL)
    ),
    CONSTRAINT chk_rec_tipo CHECK (tipo IN ('RECEITA', 'DESPESA'))
);

CREATE INDEX ix_recorrencias_espaco   ON recorrencias(espaco_id);
CREATE INDEX ix_recorrencias_entidade ON recorrencias(espaco_id, entidade_id);
CREATE INDEX ix_recorrencias_ativa    ON recorrencias(ativa) WHERE ativa = TRUE;

-- Vínculo do lançamento gerado com sua recorrência pai
ALTER TABLE transacoes   ADD COLUMN origem_recorrencia_id BIGINT NULL REFERENCES recorrencias(id) ON DELETE SET NULL;
ALTER TABLE itens_fatura ADD COLUMN origem_recorrencia_id BIGINT NULL REFERENCES recorrencias(id) ON DELETE SET NULL;

CREATE INDEX ix_transacoes_recorrencia   ON transacoes(origem_recorrencia_id)   WHERE origem_recorrencia_id IS NOT NULL;
CREATE INDEX ix_itens_fatura_recorrencia ON itens_fatura(origem_recorrencia_id) WHERE origem_recorrencia_id IS NOT NULL;

-- Idempotência: um lançamento por mês por recorrência.
-- data é TEXT legado 'YYYY-MM-DD'; LEFT(data, 7) extrai 'YYYY-MM' — string pura, IMMUTABLE.
CREATE UNIQUE INDEX uq_transacoes_recorrencia_mes
    ON transacoes (origem_recorrencia_id, LEFT(data, 7))
    WHERE origem_recorrencia_id IS NOT NULL;

CREATE UNIQUE INDEX uq_itens_fatura_recorrencia_mes
    ON itens_fatura (origem_recorrencia_id, LEFT(data, 7))
    WHERE origem_recorrencia_id IS NOT NULL;

-- Limpeza de dados: apaga meses futuros pré-criados (fixa = true)
-- e desativa o flag nas linhas remanescentes (mês atual / passados).
-- Usuário recadastrará recorrências na nova tela.

DELETE FROM transacoes
WHERE fixa = TRUE
  AND DATE_TRUNC('month', data::date) > DATE_TRUNC('month', CURRENT_DATE);

UPDATE transacoes
SET    fixa = FALSE, serie_ativa = FALSE
WHERE  fixa = TRUE;

DELETE FROM itens_fatura
WHERE fixa = TRUE
  AND DATE_TRUNC('month', data::date) > DATE_TRUNC('month', CURRENT_DATE);

UPDATE itens_fatura
SET    fixa = FALSE, serie_ativa = FALSE
WHERE  fixa = TRUE;
