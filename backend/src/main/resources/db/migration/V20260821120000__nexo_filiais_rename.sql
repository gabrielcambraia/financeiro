-- Renomeia o conceito "Entidade" (CPF/CNPJ por espaço) para "Filial" em todo o schema.
ALTER TABLE entidades RENAME TO filiais;

-- Colunas FK nas tabelas de domínio que ainda mantinham entidade_id (após V20260819160000)
ALTER TABLE contas        RENAME COLUMN entidade_id TO filial_id;
ALTER TABLE categorias    RENAME COLUMN entidade_id TO filial_id;
ALTER TABLE metas         RENAME COLUMN entidade_id TO filial_id;
ALTER TABLE orcamentos    RENAME COLUMN entidade_id TO filial_id;
ALTER TABLE centros_custo RENAME COLUMN entidade_id TO filial_id;

-- Índices: renomear para manter padrão de nomenclatura
ALTER INDEX ix_entidades_doc_espaco        RENAME TO ix_filiais_doc_espaco;
ALTER INDEX ix_entidades_espaco            RENAME TO ix_filiais_espaco;
ALTER INDEX ix_contas_espaco_entidade      RENAME TO ix_contas_espaco_filial;
ALTER INDEX ix_categorias_espaco_entidade  RENAME TO ix_categorias_espaco_filial;
ALTER INDEX ix_metas_espaco_entidade       RENAME TO ix_metas_espaco_filial;
ALTER INDEX ix_orcamentos_espaco_entidade  RENAME TO ix_orcamentos_espaco_filial;
ALTER INDEX ix_cc_espaco_entidade          RENAME TO ix_cc_espaco_filial;
