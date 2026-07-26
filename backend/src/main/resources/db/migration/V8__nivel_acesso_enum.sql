-- Troca o boolean solto usuarios.admin (V7) por um enum de verdade
-- (NivelAcesso: USUARIO/ADMIN) — papel GLOBAL, não confundir com
-- usuarios_espacos.papel (DONO/MEMBRO), que é por espaço.

ALTER TABLE usuarios ADD COLUMN nivel_acesso TEXT NOT NULL DEFAULT 'USUARIO';
UPDATE usuarios SET nivel_acesso = 'ADMIN' WHERE admin = true;
ALTER TABLE usuarios DROP COLUMN admin;
