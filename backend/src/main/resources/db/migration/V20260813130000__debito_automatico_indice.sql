CREATE INDEX idx_transacoes_debito_automatico
    ON transacoes (espaco_id, data_vencimento)
    WHERE debito_automatico = true AND data_pagamento IS NULL AND data_cancelamento IS NULL;
