package com.financeiro;

import com.financeiro.entity.enums.TipoPessoa;
import com.financeiro.service.ValidadorDocumento;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa {@link ValidadorDocumento} isoladamente (sem Spring) — sanitização de
 * máscara, dígitos verificadores de CPF e CNPJ numérico, e o novo formato de
 * CNPJ alfanumérico da Receita (a partir de jul/2026).
 */
class ValidadorDocumentoTest {

    private final ValidadorDocumento validador = new ValidadorDocumento();

    // ──── CPF ────

    @Test
    void cpf_comMascara_retornaDigitos() {
        assertThat(validador.limparEValidar("529.982.247-25", TipoPessoa.FISICA)).isEqualTo("52998224725");
    }

    @Test
    void cpf_semMascara_aceito() {
        assertThat(validador.limparEValidar("52998224725", TipoPessoa.FISICA)).isEqualTo("52998224725");
    }

    @Test
    void cpf_digitoVerificadorErrado_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("529.982.247-99", TipoPessoa.FISICA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CPF inválido");
    }

    @Test
    void cpf_todosDigitosIguais_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("111.111.111-11", TipoPessoa.FISICA))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cpf_comprimentoErrado_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("123456", TipoPessoa.FISICA))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ──── CNPJ numérico ────

    @Test
    void cnpj_numericoComMascara_retornaDigitos() {
        // 11.222.333/0001-81 — CNPJ de referência do projeto, DVs corretos
        assertThat(validador.limparEValidar("11.222.333/0001-81", TipoPessoa.JURIDICA)).isEqualTo("11222333000181");
    }

    @Test
    void cnpj_numericoSemMascara_aceito() {
        assertThat(validador.limparEValidar("11222333000181", TipoPessoa.JURIDICA)).isEqualTo("11222333000181");
    }

    @Test
    void cnpj_digitoVerificadorErrado_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("11.222.333/0001-99", TipoPessoa.JURIDICA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CNPJ inválido");
    }

    @Test
    void cnpj_todosDigitosIguais_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("11.111.111/1111-11", TipoPessoa.JURIDICA))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cnpj_comprimentoErrado_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("0000001", TipoPessoa.JURIDICA))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ──── CNPJ alfanumérico (novo padrão Receita, jul/2026) ────
    //
    // Posições 1–12: [A-Z0-9]; posições 13–14: dígitos verificadores [0-9].
    // Cálculo dos DVs usa char - '0' (A=17, Z=42) — retrocompatível com CNPJ numérico.
    //
    // CNPJ de teste: "AAAAAAAA000191"
    //   s1 = 8×17 com pesos {5,4,3,2,9,8,7,6} + 1×2 = 750 → 750%11=2 → d1=9
    //   s2 = 8×17 com pesos {6,5,4,3,2,9,8,7} + 1×3 + 9×2 = 769 → 769%11=10 → d2=1

    @Test
    void cnpj_alfanumericoComMascara_retornaUppercase() {
        assertThat(validador.limparEValidar("AA.AAA.AAA/0001-91", TipoPessoa.JURIDICA))
                .isEqualTo("AAAAAAAA000191");
    }

    @Test
    void cnpj_alfanumericoMinusculo_normalizadoEValidado() {
        assertThat(validador.limparEValidar("aa.aaa.aaa/0001-91", TipoPessoa.JURIDICA))
                .isEqualTo("AAAAAAAA000191");
    }

    @Test
    void cnpj_alfanumericoDvErrado_lanca422() {
        assertThatThrownBy(() -> validador.limparEValidar("AA.AAA.AAA/0001-99", TipoPessoa.JURIDICA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CNPJ inválido");
    }

    @Test
    void cnpj_letraNasPosicoesDv_lanca422() {
        // Posições 13–14 só aceitam dígitos numéricos
        assertThatThrownBy(() -> validador.limparEValidar("AAAAAAAA0001AB", TipoPessoa.JURIDICA))
                .isInstanceOf(ResponseStatusException.class);
    }
}
