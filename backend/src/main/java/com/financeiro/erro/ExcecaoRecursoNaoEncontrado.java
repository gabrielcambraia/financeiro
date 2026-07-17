package com.financeiro.erro;

/**
 * Lançada quando um recurso referenciado por id (conta, categoria,
 * transação) não existe ou não pertence ao espaço do usuário autenticado.
 * Mapeada para 404 por {@link TratadorGlobalExcecoes}.
 */
public class ExcecaoRecursoNaoEncontrado extends RuntimeException {

    public ExcecaoRecursoNaoEncontrado(String mensagem) {
        super(mensagem);
    }
}
