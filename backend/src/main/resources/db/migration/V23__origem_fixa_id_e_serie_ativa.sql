-- Vincula linhas de uma transação fixa em séries: cada linha aponta para
-- a "cabeça" da série (a linha mais antiga do grupo, ou ela mesma quando é cabeça).
-- serie_ativa=false encerra a série para o agendador sem apagar o histórico
-- (ex.: ao editar valor com escopo FUTURAS, o trilho antigo fica inativo).
ALTER TABLE transacoes ADD COLUMN origem_fixa_id BIGINT
  REFERENCES transacoes(id) ON DELETE SET NULL;

ALTER TABLE transacoes ADD COLUMN serie_ativa BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_transacoes_origem_fixa_id
  ON transacoes(origem_fixa_id)
  WHERE origem_fixa_id IS NOT NULL;

-- Backfill: para cada grupo (espaco_id, conta_id, valor, tipo, descricao) com
-- fixa=true, a linha de menor data vira a cabeça (origem = próprio id); as
-- demais apontam para ela.
WITH grupos AS (
  SELECT id,
         FIRST_VALUE(id) OVER (
           PARTITION BY espaco_id, conta_id, valor, tipo, COALESCE(descricao, '')
           ORDER BY data ASC, id ASC
         ) AS cabeca_id
  FROM transacoes WHERE fixa = true
)
UPDATE transacoes t
   SET origem_fixa_id = g.cabeca_id
  FROM grupos g
 WHERE t.id = g.id;
