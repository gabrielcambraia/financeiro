-- Autoria de transações (quem criou) e senha temporária obrigatória para
-- membros adicionados por um DONO. Sem FK inline em usuario_id (mesma
-- limitação do SQLite com ALTER TABLE + NOT NULL + REFERENCES já documentada
-- na V5) — posse garantida na aplicação.
--
-- Backfill: não existe "criador histórico" para transações já existentes;
-- atribuídas ao usuário local (id=1), o mesmo critério usado na V5 para
-- espaco_id.

ALTER TABLE transacoes ADD COLUMN usuario_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE usuarios ADD COLUMN precisa_trocar_senha INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_transacoes_usuario ON transacoes(usuario_id);
