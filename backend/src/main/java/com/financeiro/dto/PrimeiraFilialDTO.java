package com.financeiro.dto;

import com.financeiro.entity.enums.TipoPessoa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PrimeiraFilialDTO {

    @NotNull
    private TipoPessoa tipoPessoa;

    @NotBlank
    private String nome;

    private String nomeFantasia;

    @NotBlank
    private String documento;

    private String inscricaoEstadual;
    private String dataNascimento;
    private String email;
    private String telefone;

    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
}
