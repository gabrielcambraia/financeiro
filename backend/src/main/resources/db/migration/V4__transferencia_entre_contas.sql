-- Transferência entre contas: cada transferência vira duas linhas em
-- `transacoes` (tipo=TRANSFERENCIA), uma de saída na conta origem e outra de
-- entrada na conta destino, vinculadas por transferencia_id. O sinal do
-- ajuste de saldo passa a depender de direcao_transferencia (SAIDA/ENTRADA)
-- em vez do tipo RECEITA/DESPESA usado pelos demais lançamentos.

ALTER TABLE transacoes ADD COLUMN transferencia_id TEXT;
ALTER TABLE transacoes ADD COLUMN direcao_transferencia TEXT;

CREATE INDEX idx_transacoes_transferencia_id ON transacoes(transferencia_id);
