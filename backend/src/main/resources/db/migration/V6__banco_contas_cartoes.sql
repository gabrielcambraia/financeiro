-- Identidade visual de banco (enum Banco no código) para contas e cartões:
-- substitui a escolha manual de cor/ícone por uma seleção de instituição
-- conhecida, com cor de marca e sigla prontas para exibição. Nullable —
-- registros existentes ficam sem banco definido e continuam usando cor/ícone.

ALTER TABLE contas  ADD COLUMN banco TEXT;
ALTER TABLE cartoes ADD COLUMN banco TEXT;
