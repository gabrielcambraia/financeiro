-- Remove entidade_id de tabelas cujo valor é sempre derivado pelo objeto pai.
-- Transacao: deriva de conta.entidade_id
-- ItemFatura/Fatura: derivam de cartao.conta_pagamento.entidade_id
-- Recorrencia: deriva de conta.entidade_id ou cartao.conta_pagamento.entidade_id
-- Cartao: deriva de conta_pagamento.entidade_id
-- Divida: deriva de conta.entidade_id
-- Ativo: deriva de conta.entidade_id

DROP INDEX IF EXISTS ix_transacoes_espaco_entidade;
ALTER TABLE transacoes DROP COLUMN IF EXISTS entidade_id;

DROP INDEX IF EXISTS ix_itens_fatura_espaco_entidade;
ALTER TABLE itens_fatura DROP COLUMN IF EXISTS entidade_id;

DROP INDEX IF EXISTS ix_faturas_espaco_entidade;
ALTER TABLE faturas DROP COLUMN IF EXISTS entidade_id;

DROP INDEX IF EXISTS ix_recorrencias_entidade;
ALTER TABLE recorrencias DROP COLUMN IF EXISTS entidade_id;

DROP INDEX IF EXISTS ix_cartoes_espaco_entidade;
ALTER TABLE cartoes DROP COLUMN IF EXISTS entidade_id;

DROP INDEX IF EXISTS ix_dividas_espaco_entidade;
ALTER TABLE dividas DROP COLUMN IF EXISTS entidade_id;

DROP INDEX IF EXISTS ix_ativos_espaco_entidade;
ALTER TABLE ativos DROP COLUMN IF EXISTS entidade_id;
