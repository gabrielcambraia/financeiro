-- Move espaco_id e papel de usuarios_espacos diretamente para usuarios (1:1)
ALTER TABLE usuarios ADD COLUMN espaco_id BIGINT;
ALTER TABLE usuarios ADD COLUMN papel VARCHAR(20) NOT NULL DEFAULT 'DONO';

UPDATE usuarios u
SET espaco_id = ue.espaco_id,
    papel     = ue.papel
FROM usuarios_espacos ue
WHERE ue.usuario_id = u.id;

ALTER TABLE usuarios ALTER COLUMN espaco_id SET NOT NULL;
ALTER TABLE usuarios ADD CONSTRAINT fk_usuarios_espaco FOREIGN KEY (espaco_id) REFERENCES espacos(id);
CREATE INDEX ix_usuarios_espaco ON usuarios(espaco_id);

DROP TABLE usuarios_espacos;
