package com.financeiro.dto;

import com.financeiro.entity.enums.TipoPessoa;

import java.time.LocalDateTime;

public record RespostaEntidade(
        Long id,
        TipoPessoa tipoPessoa,
        String nome,
        String nomeFantasia,
        String documento,
        String inscricaoEstadual,
        String dataNascimento,
        String email,
        String telefone,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}
