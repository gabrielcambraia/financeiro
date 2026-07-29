ALTER TABLE contas ADD COLUMN saldo_inicial NUMERIC(19,2) NOT NULL DEFAULT 0;

-- Backfill: saldo_inicial = saldo atual - soma dos movimentos já aplicados ao saldo.
-- Todas as transações que afetaram contas.saldo estão em `transacoes` com saldo_ajustado = true
-- (aportes/resgates de metas e ativos já viram Transacao antes de ajustar o saldo; itens de
-- fatura, parcelas de dívida e rendimentos nunca tocam saldo diretamente).
UPDATE contas c SET saldo_inicial = c.saldo - COALESCE((
    SELECT SUM(CASE
        WHEN t.tipo = 'TRANSFERENCIA' THEN
            CASE WHEN t.direcao_transferencia = 'ENTRADA' THEN t.valor ELSE -t.valor END
        WHEN t.tipo = 'RECEITA' THEN t.valor
        ELSE -t.valor
    END)
    FROM transacoes t
    WHERE t.conta_id = c.id AND t.saldo_ajustado = true
), 0);
