#!/usr/bin/env python3
"""
Exporta os dados de um `financeiro.db` (SQLite, modo desktop-local antigo)
para um arquivo `.sql` com INSERTs prontos para carregar num Postgres já
com o schema baseline (V1__esquema_inicial.sql) aplicado.

Uso:
    python exportar_sqlite_para_postgres.py caminho/para/financeiro.db > dados.sql
    python exportar_sqlite_para_postgres.py caminho/para/financeiro.db --saida dados.sql

Depois de gerar o arquivo:
    1. Suba o app no Railway uma vez (Flyway cria o schema vazio).
    2. Carregue os dados:  psql "<DATABASE_URL pública>" -f dados.sql
       (o próprio script já inclui os comandos de reset de identity no final,
       então nenhum passo manual de sequence é necessário.)
    3. "Reivindique" o usuário id=1 (dono do dado migrado, que veio do modo
       desktop-local com senha_hash nulo). Esse passo é MANUAL e fica fora do
       escopo deste script — gere um hash BCrypt (o mesmo encoder usado pelo
       app, BCryptPasswordEncoder) e rode:

           UPDATE usuarios
           SET email = '<seu-email>',
               senha_hash = '<hash BCrypt gerado>',
               precisa_trocar_senha = false
           WHERE id = 1;

       Depois disso o dono loga com o próprio e-mail/senha e vê todos os
       dados migrados no espaço 1.

Só usa a biblioteca padrão do Python 3 (`sqlite3`, `argparse`, `sys`) —
sem dependências externas.
"""

import argparse
import sqlite3
import sys

# Ordem de exportação respeita as FKs do schema Postgres: espaços e usuários
# antes do vínculo N:N; categorias e contas (que dependem de espaco) antes
# de transações (que depende de conta, categoria, espaço e usuário).
TABELAS = ["espacos", "usuarios", "usuarios_espacos", "categorias", "contas", "transacoes"]

# Colunas que no SQLite são INTEGER 0/1 e no Postgres são BOOLEAN.
COLUNAS_BOOLEANAS = {
    "usuarios": {"precisa_trocar_senha"},
    "transacoes": {"fixa", "saldo_ajustado"},
}

# Tabelas com PK simples (id) — recebem reset de identity ao final.
# usuarios_espacos tem PK composta (usuario_id, espaco_id) e não usa identity.
TABELAS_COM_ID_SIMPLES = ["espacos", "usuarios", "categorias", "contas", "transacoes"]


def escapar_string(valor: str) -> str:
    """Escapa aspas simples para uso dentro de uma string literal do Postgres."""
    return valor.replace("'", "''")


def formatar_valor(valor, coluna: str, booleanas: set) -> str:
    if valor is None:
        return "NULL"
    if coluna in booleanas:
        return "true" if str(valor) in ("1", "true", "True") else "false"
    if isinstance(valor, (int, float)):
        return str(valor)
    return "'" + escapar_string(str(valor)) + "'"


def gerar_inserts(conexao: sqlite3.Connection, tabela: str, saida) -> int:
    cursor = conexao.execute(f"SELECT * FROM {tabela}")
    colunas = [descricao[0] for descricao in cursor.description]
    booleanas = COLUNAS_BOOLEANAS.get(tabela, set())

    total_linhas = 0
    for linha in cursor:
        valores = [formatar_valor(linha[coluna], coluna, booleanas) for coluna in colunas]
        colunas_sql = ", ".join(colunas)
        valores_sql = ", ".join(valores)
        saida.write(f"INSERT INTO {tabela} ({colunas_sql}) VALUES ({valores_sql});\n")
        total_linhas += 1

    return total_linhas


def gerar_reset_identities(saida) -> None:
    saida.write("\n-- Reseta as sequences de identity para MAX(id)+1 evitar colisão\n")
    saida.write("-- de PK no próximo INSERT feito pela aplicação. pg_get_serial_sequence\n")
    saida.write("-- funciona também para colunas GENERATED ... AS IDENTITY (Postgres 10+).\n")
    for tabela in TABELAS_COM_ID_SIMPLES:
        saida.write(
            f"SELECT setval(pg_get_serial_sequence('{tabela}','id'), "
            f"COALESCE((SELECT MAX(id) FROM {tabela}), 1));\n"
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Exporta os dados de financeiro.db (SQLite) para INSERTs Postgres.")
    parser.add_argument("caminho_sqlite", help="Caminho para o arquivo financeiro.db")
    parser.add_argument(
        "--saida", "-o",
        help="Arquivo de saída (.sql). Se omitido, escreve no stdout.",
        default=None,
    )
    argumentos = parser.parse_args()

    conexao = sqlite3.connect(argumentos.caminho_sqlite)
    conexao.row_factory = sqlite3.Row

    saida = open(argumentos.saida, "w", encoding="utf-8") if argumentos.saida else sys.stdout
    try:
        saida.write("-- Gerado por exportar_sqlite_para_postgres.py\n")
        saida.write("-- Carregar com: psql \"<DATABASE_URL>\" -f dados.sql\n\n")
        saida.write("BEGIN;\n\n")

        for tabela in TABELAS:
            total = gerar_inserts(conexao, tabela, saida)
            saida.write(f"-- {tabela}: {total} linha(s)\n\n")

        saida.write("COMMIT;\n")

        gerar_reset_identities(saida)
    finally:
        if argumentos.saida:
            saida.close()
        conexao.close()


if __name__ == "__main__":
    main()
