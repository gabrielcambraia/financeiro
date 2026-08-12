package com.financeiro.dto;

public record RespostaConsultaCep(
        String cep,
        String logradouro,
        String bairro,
        String cidade,
        String uf
) {}
