ALTER TABLE itens_fatura ADD COLUMN fixa BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE itens_fatura ADD COLUMN origem_fixa_id BIGINT NULL REFERENCES itens_fatura(id) ON DELETE SET NULL;
ALTER TABLE itens_fatura ADD COLUMN serie_ativa BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_itens_fatura_origem_fixa ON itens_fatura(origem_fixa_id) WHERE origem_fixa_id IS NOT NULL;
