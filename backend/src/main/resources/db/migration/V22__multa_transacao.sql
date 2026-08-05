-- Multa por atraso de pagamento, informada manualmente ao pagar uma despesa
-- (ex.: fatura de cartão paga após o vencimento). Nulo = sem multa.
ALTER TABLE transacoes ADD COLUMN multa NUMERIC(19,2);
