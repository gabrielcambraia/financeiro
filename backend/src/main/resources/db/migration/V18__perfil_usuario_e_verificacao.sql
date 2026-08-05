-- Campos de perfil expandido e verificação de contato no usuário
ALTER TABLE usuarios ADD COLUMN telefone              TEXT;
ALTER TABLE usuarios ADD COLUMN email_verificado_em   TIMESTAMP;
ALTER TABLE usuarios ADD COLUMN telefone_verificado_em TIMESTAMP;
ALTER TABLE usuarios ADD COLUMN atualizado_em         TIMESTAMP;
