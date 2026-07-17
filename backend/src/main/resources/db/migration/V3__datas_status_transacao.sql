-- Introduz o modelo de status nas transações: além da data de competência
-- (`data`), passam a existir data de vencimento, data de pagamento e data de
-- cancelamento. O saldo da conta passa a ser guiado por `data_pagamento`
-- (quitação manual) em vez de `data <= hoje`. O backfill preserva o estado
-- atual: transações já com saldo_ajustado=true viram "pagas" na própria data;
-- as demais viram "pendentes" (sem data_pagamento), o que é coerente porque
-- ainda não haviam afetado o saldo.

ALTER TABLE transacoes ADD COLUMN data_vencimento TEXT;
ALTER TABLE transacoes ADD COLUMN data_pagamento TEXT;
ALTER TABLE transacoes ADD COLUMN data_cancelamento TEXT;

UPDATE transacoes SET data_vencimento = data;

UPDATE transacoes SET data_pagamento = data WHERE saldo_ajustado = true;

CREATE INDEX idx_transacoes_data_vencimento ON transacoes(data_vencimento);
