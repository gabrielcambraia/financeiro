-- V20260821130000 deixou DEFAULT 'DONO' em usuarios.papel só para viabilizar
-- o backfill (ALTER COLUMN ... SET NOT NULL) a partir de usuarios_espacos.
-- Sem esse default, qualquer INSERT futuro que esqueça o campo falha em vez
-- de silenciosamente criar um DONO.
ALTER TABLE usuarios ALTER COLUMN papel DROP DEFAULT;
